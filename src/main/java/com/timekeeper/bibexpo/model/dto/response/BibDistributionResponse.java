package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Bib distribution details")
public class BibDistributionResponse {

    @Schema(description = "Success status", example = "true")
    private Boolean success;

    @Schema(description = "Bib number", example = "10001")
    private String bibNumber;

    @Schema(description = "Timestamp when bib was collected", example = "2026-01-15T10:30:00")
    private String collectedAt;

    @Schema(description = "Name of person who collected the bib", example = "John Doe")
    private String collectedByName;

    @Schema(description = "Phone number of person who collected the bib", example = "+919876543210")
    private String collectedByPhone;

    @Schema(description = "User ID of staff member who distributed the bib", example = "1")
    private Long distributedByUserId;

    @Schema(description = "Username of staff member who distributed the bib", example = "root")
    private String distributedByUsername;

    @Schema(description = "List of goodies items distributed at the same time",
            example = "[\"T-Shirt\", \"Cap\", \"Medal\"]")
    private List<String> goodiesDistributed;
}
