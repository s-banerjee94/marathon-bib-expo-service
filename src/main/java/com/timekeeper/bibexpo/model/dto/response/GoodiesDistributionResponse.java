package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Goodies distribution details")
public class GoodiesDistributionResponse {

    @Schema(description = "Success status", example = "true")
    private Boolean success;

    @Schema(description = "Bib number", example = "10001")
    private String bibNumber;

    @Schema(description = "List of goodies items distributed", example = "[\"T-Shirt\", \"Cap\"]")
    private List<String> itemsDistributed;

    @Schema(description = "Timestamp when items were distributed", example = "2026-01-15T10:30:00")
    private String distributedAt;

    @Schema(description = "User ID of staff member who distributed the items", example = "1")
    private Long distributedByUserId;

    @Schema(description = "Username of staff member who distributed the items", example = "root")
    private String distributedByUsername;
}
