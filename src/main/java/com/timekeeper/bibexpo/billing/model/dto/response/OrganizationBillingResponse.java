package com.timekeeper.bibexpo.billing.model.dto.response;

import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Every bill for one organization, rolled up across its events")
public class OrganizationBillingResponse {

    @Schema(description = "Organization the bills belong to", example = "7")
    private Long organizationId;

    @Schema(description = "Currency of the totals", example = "INR")
    private String currency;

    @Schema(description = "Sum of totalAmount across the bills", example = "184000.00")
    private BigDecimal totalBilled;

    @Schema(description = "The organization's bills, newest first")
    private List<BillResponse> bills;
}
