package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Column-mapping instructions sent by the frontend alongside the CSV file. Columns are matched
 * by zero-based physical position ({@code csvColumnIndex}); {@code csvColumn} is the original
 * header text, kept for display and as the stored key for goodies / additional fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Dynamic column-mapping for a CSV participant import")
public class ImportMappingRequest {

    @Schema(description = "Original uploaded file name", example = "participants.csv")
    private String fileName;

    @Schema(description = "Field delimiter used in the CSV", example = ",")
    private String delimiter;

    @Schema(description = "Whether the first row is a header row that must be skipped", example = "true")
    private boolean hasHeader;

    @Schema(description = "Total data rows reported by the frontend", example = "99")
    private Integer totalRows;

    @Schema(description = "All column headers detected in the file, in order")
    private List<String> csvColumns;

    @Schema(description = "Columns mapped to known participant fields")
    @Builder.Default
    private List<ColumnMapping> mappings = new ArrayList<>();

    @Schema(description = "Columns stored as goodies (multiple allowed; blank cells allowed)")
    @Builder.Default
    private List<ExtraColumn> goodies = new ArrayList<>();

    @Schema(description = "Columns the organizer wants to retain as free-form additional data")
    @Builder.Default
    private List<ExtraColumn> other = new ArrayList<>();

    /**
     * A single CSV column bound to a known participant field.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ColumnMapping {
        @Schema(description = "Zero-based physical column position in the file", example = "1")
        private Integer csvColumnIndex;

        @Schema(description = "Original column header", example = "BIB No")
        private String csvColumn;

        @Schema(description = "Target participant field key", example = "bibNumber")
        private String targetField;
    }

    /**
     * A CSV column retained without a fixed field, used for goodies and additional data.
     * The stored map key is {@link #csvColumn}.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExtraColumn {
        @Schema(description = "Zero-based physical column position in the file", example = "12")
        private Integer csvColumnIndex;

        @Schema(description = "Original column header, used as the stored key", example = "T-Shirt size")
        private String csvColumn;
    }
}
