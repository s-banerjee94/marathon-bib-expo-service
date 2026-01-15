package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to collect a bib")
public class CollectBibRequest {

    @Schema(description = "Name of person collecting the bib (defaults to participant if not provided)",
            example = "John Doe")
    private String collectorName;

    @Schema(description = "Phone number of person collecting the bib (defaults to participant if not provided)",
            example = "+919876543210")
    private String collectorPhone;

    @Schema(description = "List of goodies items to distribute at the same time (optional)",
            example = "[\"T-Shirt\", \"Cap\", \"Medal\"]")
    private List<String> goodiesItems;
}
