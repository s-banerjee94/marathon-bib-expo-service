package com.timekeeper.bibexpo.importer.model.dto.response;

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

    @Schema(description = "Type of error. DUPLICATE_BIB is a bib repeated within the file, or, in an ADD_ON run, "
            + "a bib already registered for the event, whose row is skipped so its record stays as it was. "
            + "LIMIT_EXCEEDED marks rows skipped once the participant limit was reached; WRITE_ERROR a record "
            + "that could not be saved.", example = "VALIDATION_ERROR",
            allowableValues = {"VALIDATION_ERROR", "PROCESSING_ERROR", "LIMIT_EXCEEDED", "DUPLICATE_BIB",
                    "DUPLICATE_CHIP", "WRITE_ERROR"})
    private String errorType;

    @Schema(description = "Field that caused the error (if applicable)", example = "email")
    private String field;

    @Schema(description = "Detailed error message", example = "Invalid email format")
    private String message;
}
