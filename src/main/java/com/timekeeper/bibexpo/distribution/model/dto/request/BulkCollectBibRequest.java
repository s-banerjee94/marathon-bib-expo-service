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
@Schema(description = "Request to collect the bibs of up to 25 participants at once, each with any goodies handed "
        + "over with it")
public class BulkCollectBibRequest {

    @Valid
    @NotEmpty(message = "Add at least one bib to collect.")
    @Size(max = 25, message = "You can collect a maximum of 25 bibs at a time.")
    @Schema(description = "The bibs to collect, at most 25, each with any goodies handed over with it",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<CollectItem> items;

    @Schema(description = "Name of the person collecting every bib in this request; each participant is their own "
            + "collector when left out", example = "John Doe", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String collectorName;

    @Schema(description = "Phone number of the person collecting every bib in this request; each participant's own "
            + "number when left out", example = "+919876543210", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String collectorPhone;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "BulkCollectItem", description = "One bib to collect, and any goodies handed over with it")
    public static class CollectItem {

        @NotBlank(message = "Every bib to collect needs its bib number.")
        @Schema(description = "Bib number", example = "3001", requiredMode = Schema.RequiredMode.REQUIRED)
        private String bibNumber;

        @Schema(description = "Goodies handed over with the bib: any on the participant's own list, or any added to "
                + "the event by hand. Leave out to collect the bib alone.",
                example = "[\"T-Shirt\", \"Medal\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private List<String> goodiesItems;

        @Schema(description = "The variant handed over, by goody name, for a goody added to the event by hand whose "
                + "inventory item comes in more than one variant. Leave every other goody out.",
                example = "{\"Sipper\": 12}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private Map<String, Long> variantIds;
    }
}
