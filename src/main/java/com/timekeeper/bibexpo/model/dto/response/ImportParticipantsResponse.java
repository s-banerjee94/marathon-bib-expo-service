package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "CSV import acknowledgment response")
public class ImportParticipantsResponse {

    @Schema(description = "Unique import job ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String importId;

    @Schema(description = "Import status", example = "COMPLETED")
    private String status;

    @Schema(description = "Total number of rows in CSV", example = "650")
    private Integer totalRows;

    @Schema(description = "Number of successfully imported participants", example = "649")
    private Integer successCount;

    @Schema(description = "Number of failed rows", example = "1")
    private Integer failureCount;

    @Schema(description = "Import result message with deletion count",
            example = "Fresh import completed. Deleted 100 old participants, imported 649 new participants. Use GET /api/events/1/imports/550e8400-e29b-41d4-a716-446655440000 for details.")
    private String message;
}
