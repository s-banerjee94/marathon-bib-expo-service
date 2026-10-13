package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "One uploaded photo to show on several variants of an item")
public class ApplyVariantImageRequest {

    @NotBlank(message = "Object key is required")
    @Schema(description = "A key from the item's upload URL, or one this item or its variants already use",
            example = "inventory-items/22/image/3f2c1d8e-....png")
    private String objectKey;

    @NotEmpty(message = "Choose at least one variant")
    @Schema(description = "The variants of this item to show the photo on", example = "[41, 42, 43, 44]")
    private List<@NotNull Long> variantIds;
}
