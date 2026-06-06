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
@Schema(description = "One organization's billing rollup, for the top-organizations list")
public class TopOrganization {

    @Schema(description = "Organization id", example = "17")
    private Long organizationId;

    @Schema(description = "Organizer name captured on the bills", example = "Pune Runners Co.")
    private String organizerName;

    @Schema(description = "Sum of totalAmount", example = "1240000.00")
    private BigDecimal totalBilled;

    @Schema(description = "Number of bills", example = "38")
    private long billsCount;
}
