package com.timekeeper.bibexpo.distribution.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Request to collect a bib")
public class CollectBibRequest {

    @Schema(description = "Name of person collecting the bib (defaults to participant if not provided)",
            example = "John Doe")
    private String collectorName;

    @Schema(description = "Phone number of person collecting the bib (defaults to participant if not provided)",
            example = "+919876543210")
    private String collectorPhone;

    @Schema(description = "Goodies to hand over at the same time (optional): any on the participant's own list, "
            + "or any added to the event by hand",
            example = "[\"T-Shirt\", \"Cap\", \"Medal\"]")
    private List<String> goodiesItems;

    @Schema(description = "The variant handed over, by goody name. Needed for a goody added to the event by hand "
            + "whose inventory item comes in more than one variant, and for one of the participant's own goodies "
            + "whose value was never taught to its item. A variant sent for one of their own goodies wins over "
            + "what their value reads as, for a size swap. Leave every other goody out.",
            example = "{\"Sipper\": 12}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Map<String, Long> variantIds;
}
