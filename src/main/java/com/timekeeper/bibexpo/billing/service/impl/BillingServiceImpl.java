package com.timekeeper.bibexpo.billing.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timekeeper.bibexpo.billing.config.BillingProperties;
import com.timekeeper.bibexpo.billing.exception.BillGenerationException;
import com.timekeeper.bibexpo.billing.exception.BillNotAllowedException;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.billing.model.dto.response.BillGenerationResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.billing.model.entity.Invoice;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.billing.repository.InvoiceRepository;
import com.timekeeper.bibexpo.service.StorageService;
import com.timekeeper.bibexpo.billing.service.BillingService;
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

import java.util.List;

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
    private final StorageService storageService;
    private final LambdaClient lambdaClient;
    private final BillingProperties billingProperties;

    @Override
    public List<BillResponse> listBills(Long eventId, User currentUser) {
        log.info("Listing bills for event ID: {} by user: {}", eventId, currentUser.getUsername());
        loadAndAuthorize(eventId, currentUser);
        return toResponses(invoiceRepository.findByEventIdOrderByCreatedAtDesc(eventId));
    }

    @Override
    public BillGenerationResponse generateBill(Long eventId, User currentUser) {
        log.info("Manual bill requested for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = loadAndAuthorize(eventId, currentUser);
        if (!isTerminal(event.getStatus())) {
            throw new BillNotAllowedException(
                    "This event must be completed or cancelled before a bill can be generated.");
        }

        String status = invokeBillingLambda(eventId);
        String message = switch (status) {
            case "CREATED" -> "Bill generated successfully.";
            case "SKIPPED_DUPLICATE" -> "A bill already exists for the current participant count.";
            case "SKIPPED_NOT_BILLABLE" -> "This event is not eligible for billing.";
            default -> throw new BillGenerationException("Bill generation failed. Please try again.");
        };

        return BillGenerationResponse.builder()
                .status(status)
                .message(message)
                .bills(toResponses(invoiceRepository.findByEventIdOrderByCreatedAtDesc(eventId)))
                .build();
    }

    /** Invoke the billing Lambda synchronously and return the {@code status} it reports. */
    private String invokeBillingLambda(Long eventId) {
        String payload = String.format("{\"eventId\":\"%d\",\"reason\":\"MANUAL\"}", eventId);
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
        return invoices.stream()
                .map(invoice -> BillResponse.fromEntity(invoice, storageService.createDownloadUrl(invoice.getPdfKey())))
                .toList();
    }

    private boolean isTerminal(EventStatus status) {
        return status == EventStatus.COMPLETED || status == EventStatus.CANCELLED;
    }
}
