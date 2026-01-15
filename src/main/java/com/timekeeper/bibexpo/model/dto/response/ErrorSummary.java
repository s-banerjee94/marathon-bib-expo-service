package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary of errors by type")
public class ErrorSummary {

    @Schema(description = "Number of validation errors", example = "45")
    private Integer validationErrors;

    @Schema(description = "Number of duplicate BIB errors", example = "3")
    private Integer duplicateBibErrors;

    @Schema(description = "Number of reference errors (race/category not found)", example = "0")
    private Integer referenceErrors;

    @Schema(description = "Number of batch write errors to DynamoDB", example = "2")
    private Integer batchWriteErrors;

    @Schema(description = "Number of general processing errors", example = "1")
    private Integer processingErrors;
}
