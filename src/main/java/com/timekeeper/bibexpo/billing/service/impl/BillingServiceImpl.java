package com.timekeeper.bibexpo.billing.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.billing.config.BillingProperties;
import com.timekeeper.bibexpo.billing.exception.BillGenerationException;
import com.timekeeper.bibexpo.billing.exception.BillNotAllowedException;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.billing.model.dto.response.BillGenerationResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.billing.model.entity.Invoice;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceLineItem;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceStatus;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.billing.repository.InvoiceLineItemRepository;
import com.timekeeper.bibexpo.billing.repository.InvoiceRepository;
import com.timekeeper.bibexpo.service.StorageService;
import com.timekeeper.bibexpo.billing.service.BillStatsTriggerService;
import com.timekeeper.bibexpo.billing.service.BillingQuotaService;
import com.timekeeper.bibexpo.billing.service.BillingScheduleService;
import com.timekeeper.bibexpo.billing.service.BillingService;
import com.timekeeper.bibexpo.billing.service.QuotaClaimResult;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
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

    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository invoiceLineItemRepository;
    private final StorageService storageService;
    private final LambdaClient lambdaClient;
    private final BillingProperties billingProperties;
    private final BillingQuotaService billingQuotaService;
    private final BillingScheduleService billingScheduleService;
    private final BillStatsTriggerService billStatsTriggerService;

    @Override
    public List<BillResponse> listBills(Long eventId, User currentUser) {
        log.info("Listing bills for event ID: {} by user: {}", eventId, currentUser.getUsername());
        loadAndAuthorize(eventId, currentUser);
        return toResponses(invoiceRepository.findByEventIdOrderByCreatedAtDesc(eventId));
    }

    @Override
    @Auditable(entityType = AuditEntityType.INVOICE, action = AuditAction.GENERATE)
    public BillGenerationResponse generateBill(Long eventId, InvoiceStatus mode, User currentUser) {
        log.info("Manual {} bill requested for event ID: {} by user: {}", mode, eventId, currentUser.getUsername());

        Event event = loadAndAuthorize(eventId, currentUser);
        if (!isTerminal(event.getStatus())) {
            throw new BillNotAllowedException(
                    "This event must be completed or cancelled before a bill can be generated.");
        }

        // Only platform admins may issue a final; organizer admins always get a draft.
        InvoiceStatus effectiveMode = isPlatformAdmin(currentUser.getRole()) ? mode : InvoiceStatus.DRAFT;

        QuotaClaimResult claim = billingQuotaService.claim(eventId, currentUser.getRole());
        switch (claim) {
            case FINALIZED -> throw new BillNotAllowedException(
                    "This event has already been finalized and cannot be billed again.");
            case QUOTA_EXHAUSTED -> throw new BillNotAllowedException(
                    "You have used all available bill requests for this event.");
            default -> { /* CLAIMED — proceed */ }
        }

        String status;
        String message;
        if (effectiveMode == InvoiceStatus.FINAL) {
            status = finalizeBill(eventId, currentUser);
            message = "Final bill generated successfully.";
        } else {
            try {
                status = invokeBillingLambda(eventId, InvoiceStatus.DRAFT);
                message = switch (status) {
                    case "CREATED_DRAFT" -> "Draft bill generated successfully.";
                    case "SKIPPED_DUPLICATE" -> "A bill already exists for the current participant count.";
                    case "SKIPPED_NOT_BILLABLE" -> "This event is not eligible for billing.";
                    case "ALREADY_FINAL" -> "This event has already been finalized and cannot be billed again.";
                    default -> throw new BillGenerationException("Bill generation failed. Please try again.");
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

        return BillGenerationResponse.builder()
                .status(status)
                .message(message)
                .bills(toResponses(invoiceRepository.findByEventIdOrderByCreatedAtDesc(eventId)))
                .build();
    }

    /**
     * Mark the event's draft FINAL and commit <em>before</em> invoking the Lambda — the mark is the
     * lock (edit endpoints reject a non-draft) and the Lambda only finalizes a bill the DB already
     * shows as FINAL. Synchronous: if the Lambda does not complete the final, undo the mark and
     * refund the caller's slot.
     */
    private String finalizeBill(Long eventId, User currentUser) {
        Invoice draft = invoiceRepository.findFirstByEventIdAndStatus(eventId, InvoiceStatus.DRAFT)
                .orElse(null);
        if (draft == null) {
            billingQuotaService.refund(eventId, currentUser.getRole());
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
            billingQuotaService.refund(eventId, currentUser.getRole());
            throw e;
        }
        if ("DISCOUNT_BELOW_ZERO".equals(status)) {
            revertToDraft(draft);
            billingQuotaService.refund(eventId, currentUser.getRole());
            throw new BillNotAllowedException(
                    "The discounts on this bill are larger than its charges, so it cannot be finalized.");
        }
        if (!"CREATED_FINAL".equals(status)) {
            revertToDraft(draft);
            billingQuotaService.refund(eventId, currentUser.getRole());
            throw new BillGenerationException("Bill generation failed. Please try again.");
        }
        // The bill is now a FINAL receivable — recompute the stats snapshot (async, best-effort).
        billStatsTriggerService.recomputeAsync("FINALIZE");
        return status;
    }

    /** Undo the FINAL mark when the Lambda did not issue the final, returning the bill to an editable draft. */
    private void revertToDraft(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setUpdatedAt(Instant.now());
        invoiceRepository.save(invoice);
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
            log.error("[billing] Lambda invoke failed for event {}: {}", eventId, e.getMessage());
            throw new BillGenerationException("Bill generation is currently unavailable. Please try again later.");
        }

        if (response.functionError() != null) {
            log.error("[billing] Lambda reported an error for event {}: {}",
                    eventId, response.payload().asUtf8String());
            throw new BillGenerationException("Bill generation failed. Please try again.");
        }

        try {
            JsonNode node = OBJECT_MAPPER.readTree(response.payload().asUtf8String());
            return node.path("status").asText("");
        } catch (Exception e) {
            log.error("[billing] could not parse Lambda response for event {}: {}", eventId, e.getMessage());
            throw new BillGenerationException("Bill generation failed. Please try again.");
        }
    }

    private Event loadAndAuthorize(Long eventId, User currentUser) {
        Event event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
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
