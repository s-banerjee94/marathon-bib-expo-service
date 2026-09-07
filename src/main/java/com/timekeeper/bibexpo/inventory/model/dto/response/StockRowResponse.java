package com.timekeeper.bibexpo.inventory.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One variant's balance at one location, with the names already resolved")
public class StockRowResponse {

    @Schema(description = "Stock row ID", example = "1")
    private Long id;

    @Schema(description = "Variant ID", example = "41")
    private Long variantId;

    @Schema(description = "The variant's attribute values joined for display, or null when the item has no real variants",
            example = "S / Yellow")
    private String variantLabel;

    @Schema(description = "Location ID", example = "9")
    private Long locationId;

    @Schema(description = "Location name", example = "Expo venue")
    private String locationName;

    @Schema(description = "Quantity on hand", example = "2200")
    private Integer onHand;

    @Schema(description = "Quantity reserved", example = "0")
    private Integer reserved;

    @Schema(description = "When this balance last changed")
    private Instant updatedAt;

    @Schema(description = "Who last changed this balance", example = "organizer1")
    private String updatedBy;
}
