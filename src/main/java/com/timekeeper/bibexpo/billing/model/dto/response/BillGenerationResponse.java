package com.timekeeper.bibexpo.billing.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Outcome of an on-demand bill request, with the event's refreshed bill list")
public class BillGenerationResponse {

    @Schema(description = "Outcome reported by the billing Lambda",
            example = "CREATED",
            allowableValues = {"CREATED", "SKIPPED_DUPLICATE", "SKIPPED_NOT_BILLABLE"})
    private String status;

    @Schema(description = "Human-readable explanation of the outcome",
            example = "Bill generated successfully.")
    private String message;

    @Schema(description = "All bills for the event, newest first, after the request")
    private List<BillResponse> bills;
}
