package com.timekeeper.bibexpo.billing.controller;

import com.timekeeper.bibexpo.billing.exception.BillNotFoundException;
import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.billing.model.dto.request.UpdatePaymentStatusRequest;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillingSummaryResponse;
import com.timekeeper.bibexpo.billing.model.entity.PaymentStatus;
import com.timekeeper.bibexpo.model.enums.DashboardRange;
import com.timekeeper.bibexpo.model.enums.TrendInterval;
import com.timekeeper.bibexpo.billing.service.BillingAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Slf4j
public class GlobalBillingController implements GlobalBillingControllerApi {

    private final BillingAdminService billingAdminService;

    @Override
    public ResponseEntity<PageableResponse<BillResponse>> listAllBills(
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        log.info("GET /api/billing — org: {} event: {} reason: {} paymentStatus: {} q: {}",
                organizationId, eventId, reason, paymentStatus, q);

        return ResponseEntity.ok(PageableResponse.of(
                billingAdminService.listAllBills(organizationId, eventId, from, to, reason, paymentStatus, q, pageable)));
    }

    @Override
    public ResponseEntity<BillingSummaryResponse> getSummary(
            @RequestParam(defaultValue = "ALL") DashboardRange range,
            @RequestParam(defaultValue = "MONTH") TrendInterval trendInterval,
            @RequestParam(defaultValue = "12") int trendBuckets,
            @RequestParam(defaultValue = "5") int topOrgs) {
        log.info("GET /api/billing/summary — range: {} trendInterval: {} trendBuckets: {} topOrgs: {}",
                range, trendInterval, trendBuckets, topOrgs);

        return ResponseEntity.ok(billingAdminService.getSummary(range, trendInterval, trendBuckets, topOrgs));
    }

    @Override
    public ResponseEntity<BillResponse> updatePaymentStatus(
            @PathVariable String billId,
            @Valid @RequestBody UpdatePaymentStatusRequest request) {
        log.info("PATCH /api/billing/{}/payment-status — {}", billId, request.getPaymentStatus());

        return ResponseEntity.ok(billingAdminService.updatePaymentStatus(billId, request.getPaymentStatus()));
    }

    // Bill lookup by id happens only in this controller's flow, so its not-found is handled here
    // rather than in the global advice.
    @ExceptionHandler(BillNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBillNotFound(BillNotFoundException ex, WebRequest request) {
        log.warn("Bill not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }
}
