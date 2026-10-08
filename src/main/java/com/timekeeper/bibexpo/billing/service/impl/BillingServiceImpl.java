package com.timekeeper.bibexpo.billing.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timekeeper.bibexpo.audit.api.Auditable;
import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.billing.config.BillingProperties;
import com.timekeeper.bibexpo.billing.exception.BillGenerationException;
import com.timekeeper.bibexpo.billing.exception.BillNotAllowedException;
import com.timekeeper.bibexpo.billing.model.dto.response.BillGenerationResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.billing.model.entity.Invoice;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceLineItem;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceStatus;
import com.timekeeper.bibexpo.billing.repository.InvoiceLineItemRepository;
import com.timekeeper.bibexpo.billing.repository.InvoiceRepository;
import com.timekeeper.bibexpo.billing.service.BillingQuotaService;
import com.timekeeper.bibexpo.billing.service.BillingScheduleService;
import com.timekeeper.bibexpo.billing.service.BillingService;
import com.timekeeper.bibexpo.billing.service.BillStatsTriggerService;
import com.timekeeper.bibexpo.billing.service.QuotaClaimResult;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.model.entity.EventStatus;
import com.timekeeper.bibexpo.event.api.EventStore;
import com.timekeeper.bibexpo.event.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.storage.service.StorageService;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    // Self-instantiated: Spring Boot 4 auto-configures a Jackson 3 mapper, not this
    // Jackson 2 type, so there is no bean of it to inject. Only used to read the
    // Lambda's small JSON response.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EventStore eventStore;
    private final EventAccessValidator eventAccessValidator;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository invoiceLineItemRepository;
    private final StorageService storageService;
    private final LambdaClient lambdaClient;
    private final BillingProperties billingProperties;
    private final BillingQuotaService billingQuotaService;
    private final BillingScheduleService billingScheduleService;
    private final BillStatsTriggerService billStatsTriggerService;
    private final EntityManager entityManager;

    @Override
    public List<BillResponse> listBills(Long eventId, User currentUser) {
        loadAndAuthorize(eventId, currentUser);
        return toResponses(invoiceRepository.findByEventIdOrderByCreatedAtDesc(eventId));
    }

    @Override
    @Auditable(entityType = AuditEntityType.INVOICE, action = AuditAction.GENERATE)
    public BillGenerationResponse generateBill(Long eventId, InvoiceStatus mode, User currentUser) {
        Event event = loadAndAuthorize(eventId, currentUser);
        if (!isTerminal(event.getStatus())) {
            throw new BillNotAllowedException(
                    "This event must be completed or cancelled before a bill can be generated.");
        }

        // Only platform admins may issue a final; organizer admins always get a draft.
        InvoiceStatus effectiveMode = isPlatformAdmin(currentUser.getRole()) ? mode : InvoiceStatus.DRAFT;

        String status;
        String message;
        if (effectiveMode == InvoiceStatus.FINAL) {
            // A final is the one closing action, gated only by the final lock — never by the
            // manual-request quota — so it stays reachable even after the draft quota is spent.
            if (billingQuotaService.isFinalized(eventId)) {
                throw new BillNotAllowedException(
                        "This event has already been finalized and cannot be billed again.");
            }
            status = finalizeBill(eventId);
            message = "Final bill generated successfully.";
        } else {
            QuotaClaimResult claim = billingQuotaService.claim(eventId, currentUser.getRole());
            switch (claim) {
                case FINALIZED -> throw new BillNotAllowedException(
                        "This event has already been finalized and cannot be billed again.");
                case QUOTA_EXHAUSTED -> throw new BillNotAllowedException(
                        "You have used all available bill requests for this event.");
                default -> { /* CLAIMED — proceed */ }
            }
            try {
                status = invokeBillingLambda(eventId, InvoiceStatus.DRAFT);
                message = switch (status) {
                    case "CREATED_DRAFT" -> "Draft bill generated successfully.";
                    case "SKIPPED_DUPLICATE" -> "A bill already exists for the current participant count.";
                    case "SKIPPED_NOT_BILLABLE" -> "This event is not eligible for billing.";
                    case "ALREADY_FINAL" -> "This event has already been finalized and cannot be billed again.";
                    default -> throw new BillGenerationException("The bill could not be generated, please try again.");
                };
            } catch (BillGenerationException e) {
                // No bill was produced — return the slot the caller just spent.
                billingQuotaService.refund(eventId, currentUser.getRole());
                throw e;
            }
        }

        // A ROOT/ADMIN engaging with billing makes the 24h auto-draft nudge redundant, so drop the
        // timer on any of their manual requests (a final also closes the event). An ORGANIZER_ADMIN
        // request leaves the timer running, so platform admins are still notified at 24h.
        if (isPlatformAdmin(currentUser.getRole())) {
            billingScheduleService.cancel(eventId);
        }

        log.atInfo()
                .addKeyValue("event", "billing.bill.generated")
                .addKeyValue("eventId", eventId)
                .addKeyValue("mode", effectiveMode)
                .addKeyValue("status", status)
                .log("Bill requested");
        return BillGenerationResponse.builder()
                .status(status)
                .message(message)
                .bills(toResponses(invoiceRepository.findByEventIdOrderByCreatedAtDesc(eventId)))
                .build();
    }

    /**
     * Mark the event's draft FINAL and commit <em>before</em> invoking the Lambda — the mark is the
     * lock (edit endpoints reject a non-draft) and the Lambda only finalizes a bill the DB already
     * shows as FINAL. Synchronous: if the Lambda does not complete the final, undo the mark so the
     * bill stays an editable draft. A final never spends a manual-request slot, so there is nothing
     * to refund on failure.
     */
    private String finalizeBill(Long eventId) {
        Invoice draft = invoiceRepository.findFirstByEventIdAndStatus(eventId, InvoiceStatus.DRAFT)
                .orElse(null);
        if (draft == null) {
            throw new BillNotAllowedException("Generate a draft bill before finalizing it.");
        }

        draft.setStatus(InvoiceStatus.FINAL);
        draft.setUpdatedAt(Instant.now());
        invoiceRepository.save(draft);

        String status;
        try {
            status = invokeBillingLambda(eventId, InvoiceStatus.FINAL);
        } catch (BillGenerationException e) {
            revertToDraft(draft);
            throw e;
        }
        if ("DISCOUNT_BELOW_ZERO".equals(status)) {
            revertToDraft(draft);
            throw new BillNotAllowedException(
                    "The discounts on this bill are larger than its charges, so it cannot be finalized.");
        }
        if (!"CREATED_FINAL".equals(status)) {
            revertToDraft(draft);
            throw new BillGenerationException("The bill could not be generated, please try again.");
        }
        // The Lambda wrote the number, totals and PDF key straight to the database; drop the copy
        // this request still holds so the response reads the issued bill, not the marked draft.
        entityManager.detach(draft);
        // The bill is now a FINAL receivable — recompute the stats snapshot (async, best-effort).
        billStatsTriggerService.recomputeAsync("FINALIZE");
        return status;
    }

    /** Undo the FINAL mark when the Lambda did not issue the final, returning the bill to an editable draft. */
    private void revertToDraft(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setUpdatedAt(Instant.now());
        invoiceRepository.save(invoice);
        log.atWarn()
                .addKeyValue("event", "billing.final.reverted")
                .addKeyValue("billId", invoice.getBillId())
                .addKeyValue("eventId", invoice.getEventId())
                .log("Final mark undone; the bill is a draft again");
    }

    /** Invoke the billing Lambda synchronously and return the {@code status} it reports. */
    private String invokeBillingLambda(Long eventId, InvoiceStatus mode) {
        String payload = String.format(
                "{\"eventId\":\"%d\",\"reason\":\"MANUAL\",\"mode\":\"%s\"}", eventId, mode.name());
        InvokeResponse response;
        try {
            response = lambdaClient.invoke(InvokeRequest.builder()
                    .functionName(billingProperties.getLambda().getArn())
                    .invocationType(InvocationType.REQUEST_RESPONSE)
                    .payload(SdkBytes.fromUtf8String(payload))
                    .build());
        } catch (SdkException e) {
            logLambdaFailure(eventId, mode, "invoke", e.getMessage());
            throw new BillGenerationException("Bill generation is unavailable right now, please try again later.");
        }

        if (response.functionError() != null) {
            logLambdaFailure(eventId, mode, "function_error", response.payload().asUtf8String());
            throw new BillGenerationException("The bill could not be generated, please try again.");
        }

        try {
            JsonNode node = OBJECT_MAPPER.readTree(response.payload().asUtf8String());
            return node.path("status").asText("");
        } catch (Exception e) {
            logLambdaFailure(eventId, mode, "unreadable_response", e.getMessage());
            throw new BillGenerationException("The bill could not be generated, please try again.");
        }
    }

    private static void logLambdaFailure(Long eventId, InvoiceStatus mode, String failure, String detail) {
        log.atError()
                .addKeyValue("event", "billing.lambda.failed")
                .addKeyValue("eventId", eventId)
                .addKeyValue("mode", mode)
                .addKeyValue("failure", failure)
                .addKeyValue("detail", detail)
                .log("Billing Lambda gave no usable result");
    }

    private Event loadAndAuthorize(Long eventId, User currentUser) {
        Event event = eventStore.requireById(eventId);
        eventAccessValidator.validateUserOrganizationAccess(currentUser, event);
        return event;
    }

    private List<BillResponse> toResponses(List<Invoice> invoices) {
        if (invoices.isEmpty()) {
            return List.of();
        }
        Map<String, List<InvoiceLineItem>> linesByInvoice = invoiceLineItemRepository
                .findByInvoiceIdInOrderByIdAsc(invoices.stream().map(Invoice::getBillId).toList())
                .stream()
                .collect(Collectors.groupingBy(InvoiceLineItem::getInvoiceId));
        return invoices.stream()
                .map(invoice -> BillResponse.fromEntity(
                        invoice,
                        linesByInvoice.getOrDefault(invoice.getBillId(), List.of()),
                        storageService.createDownloadUrl(invoice.getPdfKey())))
                .toList();
    }

    private boolean isTerminal(EventStatus status) {
        return status == EventStatus.COMPLETED || status == EventStatus.CANCELLED;
    }

    private boolean isPlatformAdmin(UserRole role) {
        return role == UserRole.ROOT || role == UserRole.ADMIN;
    }
}
