package com.timekeeper.bibexpo.distribution.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Request to hand goodies to up to 25 participants at once, each with their own goodies")
public class BulkDistributeGoodiesRequest {

    @Valid
    @NotEmpty(message = "Add at least one participant to hand goodies to.")
    @Size(max = 25, message = "You can hand goodies to a maximum of 25 participants at a time.")
    @Schema(description = "The participants to hand goodies to, at most 25, each with their own goodies",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DistributionItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "One participant, and the goodies handed to them")
    public static class DistributionItem {

        @NotBlank(message = "Every entry needs its bib number.")
        @Schema(description = "Bib number", example = "10001", requiredMode = Schema.RequiredMode.REQUIRED)
        private String bibNumber;

        @NotEmpty(message = "Every entry needs at least one goody to hand over.")
        @Schema(description = "Goodies to hand over: any on the participant's own list, or any added to the event by hand",
                example = "[\"T-Shirt\", \"Cap\", \"Medal\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<String> goodiesItems;

        @Schema(description = "The variant handed over, by goody name, for a goody added to the event by hand whose "
                + "inventory item comes in more than one variant. Leave every other goody out.",
                example = "{\"Sipper\": 12}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private Map<String, Long> variantIds;
    }
}
