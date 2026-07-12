package com.timekeeper.bibexpo.billing.controller;

import com.timekeeper.bibexpo.billing.model.dto.request.UpdatePaymentStatusRequest;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillStatsRefreshResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillStatsResponse;
import com.timekeeper.bibexpo.billing.model.entity.PaymentStatus;
import com.timekeeper.bibexpo.model.enums.DashboardRange;
import com.timekeeper.bibexpo.billing.service.BillStatsService;
import com.timekeeper.bibexpo.billing.service.BillingAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Slf4j
public class GlobalBillingController implements GlobalBillingControllerApi {

    private final BillingAdminService billingAdminService;
    private final BillStatsService billStatsService;

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
    public ResponseEntity<BillStatsResponse> getStats(
            @RequestParam(defaultValue = "ALL") DashboardRange range) {
        log.info("GET /api/billing/stats — range: {}", range);

        return ResponseEntity.ok(billStatsService.getStats(range));
    }

    @Override
    public ResponseEntity<BillStatsRefreshResponse> refreshStats() {
        log.info("POST /api/billing/stats/refresh");

        return ResponseEntity.accepted().body(billStatsService.refresh());
    }

    @Override
    public ResponseEntity<BillResponse> updatePaymentStatus(
            @PathVariable String billId,
            @Valid @RequestBody UpdatePaymentStatusRequest request) {
        log.info("PATCH /api/billing/{}/payment-status — {}", billId, request.getPaymentStatus());

        return ResponseEntity.ok(billingAdminService.updatePaymentStatus(billId, request.getPaymentStatus()));
    }
}
