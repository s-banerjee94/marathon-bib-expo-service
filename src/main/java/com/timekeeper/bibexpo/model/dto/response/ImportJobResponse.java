package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Import job details")
public class ImportJobResponse {

    @Schema(description = "Unique import job ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String importId;

    @Schema(description = "Event ID", example = "1")
    private Long eventId;

    @Schema(description = "Event name", example = "Mumbai Marathon 2024")
    private String eventName;

    @Schema(description = "Original CSV filename", example = "participants.csv")
    private String fileName;

    @Schema(description = "Total rows in CSV", example = "100")
    private Integer totalRows;

    @Schema(description = "Successfully imported count", example = "98")
    private Integer successCount;

    @Schema(description = "Failed rows count", example = "2")
    private Integer failureCount;

    @Schema(description = "Error summary by type")
    private ErrorSummary errorSummary;

    @Schema(description = "Import status", example = "COMPLETED")
    private String status;

    @Schema(description = "User ID who initiated the import", example = "5")
    private Long importedBy;

    @Schema(description = "Import timestamp", example = "2024-01-15T10:30:00")
    private LocalDateTime importedAt;
}
