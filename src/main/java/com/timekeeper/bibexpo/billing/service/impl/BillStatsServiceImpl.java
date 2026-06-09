package com.timekeeper.bibexpo.billing.service.impl;

import com.timekeeper.bibexpo.billing.config.BillingRates;
import com.timekeeper.bibexpo.billing.model.dto.BillStatsSnapshotData;
import com.timekeeper.bibexpo.billing.model.dto.response.AgingBucket;
import com.timekeeper.bibexpo.billing.model.dto.response.BillStatsRefreshResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillStatsResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillStatsTrend;
import com.timekeeper.bibexpo.billing.model.dto.response.BilledStat;
import com.timekeeper.bibexpo.billing.model.dto.response.GstStat;
import com.timekeeper.bibexpo.billing.model.dto.response.MoneyStat;
import com.timekeeper.bibexpo.billing.model.entity.BillingStatsSnapshot;
import com.timekeeper.bibexpo.billing.repository.BillingStatsSnapshotRepository;
import com.timekeeper.bibexpo.billing.service.BillStatsService;
import com.timekeeper.bibexpo.billing.service.BillStatsTriggerService;
import com.timekeeper.bibexpo.model.enums.DashboardRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillStatsServiceImpl implements BillStatsService {

    private static final List<String> AGING_BANDS = List.of("0-30", "31-60", "61-90", "90+");

    private final BillingStatsSnapshotRepository snapshotRepository;
    private final BillStatsTriggerService triggerService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public BillStatsResponse getStats(DashboardRange range) {
        BillingStatsSnapshot snapshot = snapshotRepository
                .findByScopeAndScopeKey(BillingStatsSnapshot.SCOPE_GLOBAL, BillingStatsSnapshot.GLOBAL_SCOPE_SENTINEL)
                .orElse(null);
        if (snapshot == null) {
            log.debug("No bill-stats snapshot yet — returning empty {} slice", range);
            return empty(range);
        }

        BillStatsSnapshotData data;
        try {
            data = objectMapper.readValue(snapshot.getSnapshotData(), BillStatsSnapshotData.class);
        } catch (JacksonException e) {
            log.warn("Could not parse bill-stats snapshot (id: {}) — returning empty slice", snapshot.getId(), e);
            return empty(range);
        }

        BillStatsResponse slice = data.getRanges() == null ? null : data.getRanges().get(range.name());
        if (slice == null) {
            return empty(range);
        }
        slice.setCurrency(data.getCurrency() != null ? data.getCurrency() : BillingRates.DEFAULT.currency());
        slice.setRange(range);
        // The column values are authoritative for when/why the snapshot was last written.
        slice.setRefreshedAt(snapshot.getRefreshedAt());
        slice.setComputedBy(snapshot.getComputedBy());
        return slice;
    }

    @Override
    public BillStatsRefreshResponse refresh() {
        var current = snapshotRepository
                .findByScopeAndScopeKey(BillingStatsSnapshot.SCOPE_GLOBAL, BillingStatsSnapshot.GLOBAL_SCOPE_SENTINEL);
        triggerService.recomputeAsync("MANUAL");
        return BillStatsRefreshResponse.builder()
                .refreshedAt(current.map(BillingStatsSnapshot::getRefreshedAt).orElse(null))
                .message("Statistics recompute has been triggered.")
                .build();
    }

    /** An all-zero slice for a range with no snapshot yet — {@code refreshedAt}/{@code computedBy} stay null. */
    private BillStatsResponse empty(DashboardRange range) {
        MoneyStat zeroMoney = MoneyStat.builder().amount(BigDecimal.ZERO).count(0).build();
        return BillStatsResponse.builder()
                .currency(BillingRates.DEFAULT.currency())
                .range(range)
                .refreshedAt(null)
                .computedBy(null)
                .billed(BilledStat.builder()
                        .amount(BigDecimal.ZERO).net(BigDecimal.ZERO).tax(BigDecimal.ZERO).count(0).build())
                .collected(zeroMoney)
                .outstanding(zeroMoney)
                .collectionRate(BigDecimal.ZERO)
                .averageBill(BigDecimal.ZERO)
                .dso(0)
                .gst(GstStat.builder()
                        .collected(BigDecimal.ZERO).outstanding(BigDecimal.ZERO).total(BigDecimal.ZERO).build())
                .byReason(Map.of("AUTO", zeroMoney, "MANUAL", zeroMoney))
                .aging(AGING_BANDS.stream()
                        .map(band -> AgingBucket.builder().bucket(band).amount(BigDecimal.ZERO).count(0).build())
                        .toList())
                .trend(BillStatsTrend.builder()
                        .interval("MONTH")
                        .bucketLabels(List.of())
                        .billed(List.of())
                        .collected(List.of())
                        .count(List.of())
                        .build())
                .topOrganizations(List.of())
                .build();
    }
}
