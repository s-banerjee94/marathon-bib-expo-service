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
@Schema(description = "A participant field a CSV column can be mapped to during import")
public class ImportFieldResponse {

    @Schema(description = "Target field key sent back as targetField in the mapping", example = "bibNumber")
    private String key;

    @Schema(description = "Human-readable label for display", example = "Bib number")
    private String label;

    @Schema(description = "Whether this field must be mapped before importing", example = "true")
    private boolean required;
}
