package com.timekeeper.bibexpo.distribution.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
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
@Schema(description = "Request to distribute goodies items")
public class DistributeGoodiesRequest {

    @NotEmpty(message = "Add at least one goody to hand over.")
    @Schema(description = "Goodies to hand over: any on the participant's own list, or any added to the event by hand",
            example = "[\"T-Shirt\", \"Cap\", \"Medal\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> goodiesItems;

    @Schema(description = "The variant handed over, by goody name, for a goody added to the event by hand whose "
            + "inventory item comes in more than one variant. Leave every other goody out.",
            example = "{\"Sipper\": 12}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Map<String, Long> variantIds;
}
