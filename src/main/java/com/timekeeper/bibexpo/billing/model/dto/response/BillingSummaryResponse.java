package com.timekeeper.bibexpo.billing.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Platform-wide billing aggregates for the selected range")
public class BillingSummaryResponse {

    @Schema(description = "Currency of all amounts", example = "INR")
    private String currency;

    @Schema(description = "When this rollup was computed (ISO-8601 instant)", example = "2026-06-06T11:48:02Z")
    private Instant refreshedAt;

    @Schema(description = "Sum of totalAmount within the range", example = "4820000.00")
    private BigDecimal totalBilled;

    @Schema(description = "Number of bills within the range", example = "312")
    private long billsCount;

    @Schema(description = "totalBilled / billsCount (zero when no bills)", example = "15448.70")
    private BigDecimal averageBill;

    @Schema(description = "Billed amount and count split by trigger reason, keyed AUTO and MANUAL")
    private Map<String, ReasonAggregate> byReason;

    @Schema(description = "Billed amount and count over time buckets")
    private BillingTrend trend;

    @Schema(description = "Highest-billed organizations within the range, descending")
    private List<TopOrganization> topOrganizations;
}
