package com.timekeeper.bibexpo.organization.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Inventory caps. Optional; any cap omitted is left as it is.")
public class InventoryQuotaRequest {

    @Min(value = 1, message = "Maximum must be at least 1")
    @Schema(description = "The organization's own vocabulary entries", example = "30",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer terms;

    @Min(value = 1, message = "Maximum must be at least 1")
    @Schema(description = "Places the organization may keep stock in", example = "50",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer locations;

    @Min(value = 1, message = "Maximum must be at least 1")
    @Schema(description = "Choices allowed on one of the organization's own attributes", example = "30",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer optionsPerAttribute;

    @Min(value = 1, message = "Maximum must be at least 1")
    @Schema(description = "Attributes one item may split its stock by", example = "2",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer variantAttributesPerItem;

    @Min(value = 1, message = "Maximum must be at least 1")
    @Schema(description = "Variant rows one item may hold", example = "30",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer variantsPerItem;
}
