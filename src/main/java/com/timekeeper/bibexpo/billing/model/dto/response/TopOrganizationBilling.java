package com.timekeeper.bibexpo.billing.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One organization's billed/collected/outstanding rollup, for the top-organizations leaderboard")
public class TopOrganizationBilling {

    @Schema(description = "Organization id", example = "7")
    private Long organizationId;

    @Schema(description = "Organizer name captured on the bills", example = "Pune Runners Co.")
    private String organizerName;

    @Schema(description = "Gross billed", example = "184000.00")
    private BigDecimal billed;

    @Schema(description = "Gross collected (paid)", example = "120000.00")
    private BigDecimal collected;

    @Schema(description = "Gross outstanding (unpaid)", example = "64000.00")
    private BigDecimal outstanding;

    @Schema(description = "Number of bills", example = "12")
    private long billsCount;
}
