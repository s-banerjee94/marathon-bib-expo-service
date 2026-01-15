package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to distribute goodies for multiple participants")
public class BulkDistributeGoodiesRequest {

    @NotEmpty(message = "Distribution items list cannot be empty")
    @Schema(description = "List of distribution items with bib number and item name",
            required = true)
    private List<DistributionItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Distribution item with bib number and goodies items list")
    public static class DistributionItem {

        @NotNull(message = "Bib number is required")
        @Schema(description = "Bib number", example = "10001", required = true)
        private String bibNumber;

        @NotEmpty(message = "At least one goodies item is required")
        @Schema(description = "List of goodies items to distribute",
                example = "[\"T-Shirt\", \"Cap\", \"Medal\"]", required = true)
        private List<String> goodiesItems;
    }
}
