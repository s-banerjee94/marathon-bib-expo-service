package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Distribution event log details")
public class DistributionLogResponse {

    @Schema(description = "Event ID", example = "1")
    private String eventId;

    @Schema(description = "Timestamp when the action was performed", example = "2024-01-15T10:30:00")
    private String timestamp;

    @Schema(description = "Bib number", example = "3001")
    private String bibNumber;

    @Schema(description = "Action type: BIB_COLLECTED, BIB_UNDONE, GOODIES_DISTRIBUTED, GOODIES_UNDONE",
            example = "BIB_COLLECTED")
    private String action;

    @Schema(description = "Goodies item name (null for bib actions)", example = "T-Shirt")
    private String itemName;

    @Schema(description = "Staff member who performed the action (format: ID__|__Username)",
            example = "123__|__john_doe")
    private String performedBy;

    @Schema(description = "Name of person who collected (for bib actions)", example = "John Doe")
    private String collectorName;

    @Schema(description = "Phone number of person who collected (for bib actions)", example = "+919876543210")
    private String collectorPhone;

    @Schema(description = "Additional details about the action", example = "Bib collection undone. All goodies distribution reset.")
    private String details;
}
