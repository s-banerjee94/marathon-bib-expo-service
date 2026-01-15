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
@Schema(description = "Structured import error with details")
public class ImportError {

    @Schema(description = "Row number where error occurred", example = "23")
    private Integer rowNumber;

    @Schema(description = "Type of error", example = "VALIDATION_ERROR",
            allowableValues = {"VALIDATION_ERROR", "DUPLICATE_BIB", "REFERENCE_ERROR", "BATCH_WRITE_ERROR", "PROCESSING_ERROR"})
    private String errorType;

    @Schema(description = "Field that caused the error (if applicable)", example = "email")
    private String field;

    @Schema(description = "Detailed error message", example = "Invalid email format")
    private String message;
}
