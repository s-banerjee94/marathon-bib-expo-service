package com.timekeeper.bibexpo.billing.model.dto.response;

import com.timekeeper.bibexpo.model.enums.DashboardRange;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One range slice of the precomputed billing statistics. The numeric fields are computed by the
 * billing-stats Lambda and read verbatim from the snapshot; {@code currency}, {@code range},
 * {@code refreshedAt} and {@code computedBy} are filled in by Spring from the snapshot row.
 *
 * <p>All figures cover FINAL (issued) bills only — drafts never count. Within a range, the FINAL
 * bills finalized in the window form the cohort, and {@code billed = collected + outstanding}
 * (both amount and count reconcile).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Billing statistics for one range window, served from a Lambda-computed snapshot")
public class BillStatsResponse {

    @Schema(description = "Currency of all amounts", example = "INR")
    private String currency;

    @Schema(description = "The range window these figures cover", example = "ALL")
    private DashboardRange range;

    @Schema(description = "When the Lambda last computed the snapshot; null if it has never run",
            example = "2026-06-08T10:00:00Z", nullable = true)
    private Instant refreshedAt;

    @Schema(description = "What last triggered the recompute (FINALIZE, PAYMENT or MANUAL); null if never run",
            example = "PAYMENT", nullable = true)
    private String computedBy;

    @Schema(description = "Gross/net/GST billed in the window")
    private BilledStat billed;

    @Schema(description = "Of the billed cohort, the part that has been paid")
    private MoneyStat collected;

    @Schema(description = "Of the billed cohort, the part still unpaid (open receivable)")
    private MoneyStat outstanding;

    @Schema(description = "collected.amount / billed.amount as a percentage (0 when nothing billed)", example = "64.52")
    private BigDecimal collectionRate;

    @Schema(description = "billed.amount / billed.count (0 when no bills)", example = "15448.70")
    private BigDecimal averageBill;

    @Schema(description = "Days sales outstanding — average age in days of the unpaid bills", example = "37")
    private int dso;

    @Schema(description = "GST liability split by collected vs outstanding")
    private GstStat gst;

    @Schema(description = "Billed amount and count split by trigger, keyed AUTO and MANUAL")
    private Map<String, MoneyStat> byReason;

    @Schema(description = "Receivables aging of the outstanding bills (0-30 / 31-60 / 61-90 / 90+)")
    private List<AgingBucket> aging;

    @Schema(description = "Billed-vs-collected over the last 12 months (range-independent)")
    private BillStatsTrend trend;

    @Schema(description = "Highest-billed organizations in the window, descending")
    private List<TopOrganizationBilling> topOrganizations;
}
