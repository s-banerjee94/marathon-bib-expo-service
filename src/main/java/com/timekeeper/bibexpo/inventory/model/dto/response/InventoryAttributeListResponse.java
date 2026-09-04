package com.timekeeper.bibexpo.inventory.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "The attributes a caller can pick from, split by owner")
public class InventoryAttributeListResponse {

    @Schema(description = "Shared by every organization and editable only by the platform administrator")
    private List<InventoryAttributeResponse> platformDefaults;

    @Schema(description = "This organization's own, which it may rename and delete")
    private List<InventoryAttributeResponse> organizationAttributes;
}
