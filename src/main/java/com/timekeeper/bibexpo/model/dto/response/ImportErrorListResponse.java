package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated list of import errors")
public class ImportErrorListResponse {

    @Schema(description = "Import job ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String importId;

    @Schema(description = "List of errors for this page")
    private List<ImportError> errors;

    @Schema(description = "Total number of errors", example = "150")
    private Integer totalErrors;

    @Schema(description = "Current page number", example = "0")
    private Integer currentPage;

    @Schema(description = "Page size", example = "50")
    private Integer pageSize;

    @Schema(description = "Total number of pages", example = "3")
    private Integer totalPages;

    @Schema(description = "Error summary by type")
    private ErrorSummary errorSummary;
}
