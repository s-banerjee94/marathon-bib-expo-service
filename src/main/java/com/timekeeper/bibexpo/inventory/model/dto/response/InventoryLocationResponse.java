package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryLocation;
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
@Schema(description = "A place stock can physically sit")
public class InventoryLocationResponse {

    @Schema(description = "Location ID", example = "1")
    private Long id;

    @Schema(description = "Owning organization", example = "1")
    private Long organizationId;

    @Schema(description = "Display name", example = "Expo Counter 1")
    private String name;

    @Schema(description = "Term ID for this location's type", example = "1")
    private Long typeId;

    @Schema(description = "When this location was added")
    private Instant createdAt;

    @Schema(description = "When this location was last changed")
    private Instant updatedAt;

    public static InventoryLocationResponse fromEntity(InventoryLocation location) {
        return InventoryLocationResponse.builder()
                .id(location.getId())
                .organizationId(location.getOrganizationId())
                .name(location.getName())
                .typeId(location.getTypeId())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}
