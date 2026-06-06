package com.timekeeper.bibexpo.billing.controller;

import com.timekeeper.bibexpo.billing.exception.BillGenerationException;
import com.timekeeper.bibexpo.billing.exception.BillNotAllowedException;
import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillGenerationResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/events/{eventId}/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingController implements BillingControllerApi {

    private final BillingService billingService;

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
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to generate a bill for event ID: {} by user: {}",
                eventId, currentUser.getUsername());

        return ResponseEntity.ok(billingService.generateBill(eventId, currentUser));
    }

    // Billing-only exceptions are handled here rather than in the global advice, since they can
    // only be thrown from this controller's flows. Cross-cutting ones stay in GlobalExceptionHandler.

    @ExceptionHandler(BillNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleBillNotAllowed(BillNotAllowedException ex, WebRequest request) {
        log.warn("Bill request not allowed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }

    @ExceptionHandler(BillGenerationException.class)
    public ResponseEntity<ErrorResponse> handleBillGeneration(BillGenerationException ex, WebRequest request) {
        log.error("Bill generation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage(), request));
    }
}
