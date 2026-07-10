package com.timekeeper.bibexpo.billing.controller;

import com.timekeeper.bibexpo.billing.exception.BillGenerationException;
import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.billing.model.dto.request.LineItemRequest;
import com.timekeeper.bibexpo.billing.model.dto.request.ParticipantLineRequest;
import com.timekeeper.bibexpo.billing.model.dto.response.BillGenerationResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceStatus;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.billing.service.BillingLineItemService;
import com.timekeeper.bibexpo.billing.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/events/{eventId}/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingController implements BillingControllerApi {

    private final BillingService billingService;
    private final BillingLineItemService billingLineItemService;

    @Override
    public ResponseEntity<List<BillResponse>> listBills(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to list bills for event ID: {} by user: {}",
                eventId, currentUser.getUsername());

        return ResponseEntity.ok(billingService.listBills(eventId, currentUser));
    }

    @Override
    public ResponseEntity<BillGenerationResponse> generateBill(
            @PathVariable Long eventId,
            @RequestParam(defaultValue = "DRAFT") InvoiceStatus mode,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to generate a {} bill for event ID: {} by user: {}",
                mode, eventId, currentUser.getUsername());

        return ResponseEntity.ok(billingService.generateBill(eventId, mode, currentUser));
    }

    @Override
    public ResponseEntity<BillResponse> addLineItem(
            @PathVariable Long eventId,
            @PathVariable String invoiceId,
            @Valid @RequestBody LineItemRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to add a {} line item to bill {} of event {} by user: {}",
                request.getKind(), invoiceId, eventId, currentUser.getUsername());

        return ResponseEntity.ok(billingLineItemService.addLineItem(eventId, invoiceId, request, currentUser));
    }

    @Override
    public ResponseEntity<BillResponse> updateLineItem(
            @PathVariable Long eventId,
            @PathVariable String invoiceId,
            @PathVariable Long lineItemId,
            @Valid @RequestBody LineItemRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to update line item {} on bill {} of event {} by user: {}",
                lineItemId, invoiceId, eventId, currentUser.getUsername());

        return ResponseEntity.ok(
                billingLineItemService.updateLineItem(eventId, invoiceId, lineItemId, request, currentUser));
    }

    @Override
    public ResponseEntity<BillResponse> updateParticipantLine(
            @PathVariable Long eventId,
            @PathVariable String invoiceId,
            @Valid @RequestBody ParticipantLineRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to update the participant line on bill {} of event {} by user: {}",
                invoiceId, eventId, currentUser.getUsername());

        return ResponseEntity.ok(
                billingLineItemService.updateParticipantLine(eventId, invoiceId, request, currentUser));
    }

    @Override
    public ResponseEntity<BillResponse> deleteLineItem(
            @PathVariable Long eventId,
            @PathVariable String invoiceId,
            @PathVariable Long lineItemId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to delete line item {} from bill {} of event {} by user: {}",
                lineItemId, invoiceId, eventId, currentUser.getUsername());

        return ResponseEntity.ok(
                billingLineItemService.deleteLineItem(eventId, invoiceId, lineItemId, currentUser));
    }

    // BillGeneration is thrown only from this controller's flows; BillNotFound/BillNotAllowed are
    // shared with the admin billing controllers and handled by the global ApiException handler.
    @ExceptionHandler(BillGenerationException.class)
    public ResponseEntity<ErrorResponse> handleBillGeneration(BillGenerationException ex, WebRequest request) {
        log.error("Bill generation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage(), request));
    }
}
