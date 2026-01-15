package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Response for undo distribution operations")
public class UndoDistributionResponse {

    @Schema(description = "Success status", example = "true")
    private Boolean success;

    @Schema(description = "Message describing the result", example = "Bib collection undone successfully")
    private String message;

    @Schema(description = "Bib number", example = "10001")
    private String bibNumber;

    @Schema(description = "Timestamp when undo was performed", example = "2026-01-15T16:53:51")
    private String undoneAt;

    @Schema(description = "User ID who performed the undo operation", example = "1")
    private Long undoneByUserId;

    @Schema(description = "Username who performed the undo operation", example = "root")
    private String undoneByUsername;
}
