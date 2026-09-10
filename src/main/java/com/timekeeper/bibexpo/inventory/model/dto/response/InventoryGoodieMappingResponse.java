package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryGoodieMapping;
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
@Schema(description = "One of an event's goodies, and the item it is handed out from")
public class InventoryGoodieMappingResponse {

    @Schema(description = "Goody link ID", example = "1")
    private Long id;

    @Schema(description = "Owning organization", example = "1")
    private Long organizationId;

    @Schema(description = "The event this link belongs to", example = "5")
    private Long eventId;

    @Schema(description = "The goodies column heading, exactly as the import stored it",
            example = "T-Shirt")
    private String goodieName;

    @Schema(description = "The inventory item this goody is handed out from", example = "12")
    private Long itemId;

    @Schema(description = "Name of that item, so the list renders without a second call",
            example = "Finisher T-Shirt")
    private String itemName;

    @Schema(description = "The location it is handed out from; null while the event is a draft and "
            + "nobody has chosen one yet", example = "4")
    private Long locationId;

    @Schema(description = "Name of that location", example = "JBG Expo Venue")
    private String locationName;

    @Schema(description = "When this link was created")
    private Instant createdAt;

    @Schema(description = "Who created this link", example = "organizer1")
    private String createdBy;

    @Schema(description = "When this link was last changed")
    private Instant updatedAt;

    @Schema(description = "Who last changed this link", example = "organizer1")
    private String updatedBy;

    public static InventoryGoodieMappingResponse of(InventoryGoodieMapping mapping, String itemName,
                                                    String locationName) {
        return InventoryGoodieMappingResponse.builder()
                .id(mapping.getId())
                .organizationId(mapping.getOrganizationId())
                .eventId(mapping.getEventId())
                .goodieName(mapping.getGoodieName())
                .itemId(mapping.getItemId())
                .itemName(itemName)
                .locationId(mapping.getLocationId())
                .locationName(locationName)
                .createdAt(mapping.getCreatedAt())
                .createdBy(mapping.getCreatedBy())
                .updatedAt(mapping.getUpdatedAt())
                .updatedBy(mapping.getLastModifiedBy())
                .build();
    }
}
