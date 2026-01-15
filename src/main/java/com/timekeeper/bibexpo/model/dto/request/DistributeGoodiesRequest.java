package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to distribute goodies items")
public class DistributeGoodiesRequest {

    @NotEmpty(message = "At least one item is required")
    @Schema(description = "List of goodies items to distribute",
            example = "[\"T-Shirt\", \"Cap\", \"Medal\"]", required = true)
    private List<String> goodiesItems;
}
