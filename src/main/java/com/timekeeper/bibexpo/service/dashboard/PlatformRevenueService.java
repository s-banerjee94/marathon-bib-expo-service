package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.billing.config.BillingRates;
import com.timekeeper.bibexpo.billing.repository.InvoiceRepository;
import com.timekeeper.bibexpo.model.dto.response.dashboard.PlatformRevenueResponse;
import com.timekeeper.bibexpo.model.dto.response.dashboard.PlatformRevenueTrendDto;
import com.timekeeper.bibexpo.model.enums.DashboardRange;
import com.timekeeper.bibexpo.model.enums.TrendInterval;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the platform revenue summary directly from the invoices ledger. "Earned" = money
 * collected (FINAL bills marked PAID), summed by paidAt within the requested range.
 */
@Service
@RequiredArgsConstructor
public class PlatformRevenueService {

    private static final String CURRENCY = BillingRates.DEFAULT.currency();

    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public PlatformRevenueResponse buildRevenue(DashboardRange range, int trendBuckets, TrendInterval trendInterval) {
        Instant now = Instant.now();
        Instant from = rangeFrom(range, now);

        BigDecimal earned = invoiceRepository.sumCollectedBetween(from, null);

        BigDecimal deltaPct = null;
        String comparisonLabel = null;
        if (range != DashboardRange.ALL) {
            long windowDays = range == DashboardRange.YEAR ? 365 : 30;
            BigDecimal prior = invoiceRepository.sumCollectedBetween(
                    now.minus(2 * windowDays, ChronoUnit.DAYS), from);
            deltaPct = percentChange(earned, prior);
            comparisonLabel = range == DashboardRange.YEAR ? "vs previous 12 months" : "vs previous 30 days";
        }

        return PlatformRevenueResponse.builder()
                .refreshedAt(now)
                .currency(CURRENCY)
                .range(range)
                .earned(earned)
                .deltaPct(deltaPct)
                .comparisonLabel(comparisonLabel)
                .trend(buildTrend(trendBuckets, trendInterval))
                .build();
    }

    private PlatformRevenueTrendDto buildTrend(int buckets, TrendInterval interval) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<LocalDate> starts = TrendBucketUtil.bucketStarts(interval, buckets, today);

        List<String> labels = new ArrayList<>(starts.size());
        List<BigDecimal> earned = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            Instant start = starts.get(i).atStartOfDay(ZoneOffset.UTC).toInstant();
            // Last (current) bucket runs open-ended to now; paidAt is never future.
            Instant end = (i < starts.size() - 1)
                    ? starts.get(i + 1).atStartOfDay(ZoneOffset.UTC).toInstant()
                    : null;
            labels.add(TrendBucketUtil.label(interval, starts.get(i)));
            earned.add(invoiceRepository.sumCollectedBetween(start, end));
        }

        return PlatformRevenueTrendDto.builder()
                .interval(interval)
                .bucketLabels(labels)
                .earned(earned)
                .build();
    }

    /** Signed percent change of current vs prior, to one decimal; null when there is no prior basis. */
    private BigDecimal percentChange(BigDecimal current, BigDecimal prior) {
        if (prior == null || prior.signum() == 0) {
            return null;
        }
        return current.subtract(prior)
                .divide(prior, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private Instant rangeFrom(DashboardRange range, Instant now) {
        return switch (range) {
            case YEAR  -> now.minus(365, ChronoUnit.DAYS);
            case MONTH -> now.minus(30, ChronoUnit.DAYS);
            case ALL   -> null;
        };
    }
}
