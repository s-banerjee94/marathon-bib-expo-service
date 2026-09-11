package com.timekeeper.bibexpo.distribution.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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
        @Schema(description = "Goodies to hand over: any on the participant's own list, or any added to the event by hand",
                example = "[\"T-Shirt\", \"Cap\", \"Medal\"]", required = true)
        private List<String> goodiesItems;

        @Schema(description = "The variant handed over, by goody name, for a goody added to the event by hand whose "
                + "inventory item comes in more than one variant. Leave every other goody out.",
                example = "{\"Sipper\": 12}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private Map<String, Long> variantIds;
    }
}
