package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryMovement;
import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
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
@Schema(description = "One line of the append-only stock ledger")
public class StockMovementResponse {

    @Schema(description = "Movement ID", example = "1")
    private Long id;

    @Schema(description = "Item ID", example = "1")
    private Long itemId;

    @Schema(description = "Item name", example = "T-Shirt")
    private String itemName;

    @Schema(description = "Variant ID", example = "1")
    private Long variantId;

    @Schema(description = "The variant's values joined for display, or null when the item varies by nothing", example = "M")
    private String variantLabel;

    @Schema(description = "Kind of ledger line", example = "TRANSFER")
    private MovementType type;

    @Schema(description = "Why this movement happened, when recorded", example = "DAMAGED")
    private MovementReason reason;

    @Schema(description = "Location the stock left, null when it entered from outside the system", example = "1")
    private Long fromLocationId;

    @Schema(description = "Name of the location the stock left, or null", example = "Warehouse")
    private String fromLocationName;

    @Schema(description = "Location the stock arrived at, null when it left to outside the system", example = "2")
    private Long toLocationId;

    @Schema(description = "Name of the location the stock arrived at, or null", example = "Expo Venue")
    private String toLocationName;

    @Schema(description = "Quantity moved", example = "900")
    private Integer quantity;

    @Schema(description = "What caused this line — a bib number, an issue slip id, a PO id", example = "Opening balance")
    private String reference;

    @Schema(description = "Whether this issue went beyond what the recipient was entitled to", example = "false")
    private Boolean beyondEntitlement;

    @Schema(description = "Username of whoever performed this action", example = "organizer1")
    private String performedBy;

    @Schema(description = "When this movement happened")
    private Instant occurredAt;

    @Schema(description = "The posting that wrote this line, or null for counter hand-outs and older lines", example = "57")
    private Long postingId;

    @Schema(description = "The undo of that posting, or null while it stands", example = "58")
    private Long reversedByPostingId;

    public static StockMovementResponse fromEntity(InventoryMovement movement) {
        return StockMovementResponse.builder()
                .id(movement.getId())
                .itemId(movement.getItemId())
                .variantId(movement.getVariantId())
                .type(movement.getType())
                .reason(movement.getReason())
                .fromLocationId(movement.getFromLocationId())
                .toLocationId(movement.getToLocationId())
                .quantity(movement.getQuantity())
                .reference(movement.getReference())
                .beyondEntitlement(movement.getBeyondEntitlement())
                .performedBy(movement.getCreatedBy())
                .occurredAt(movement.getOccurredAt())
                .postingId(movement.getPostingId())
                .build();
    }
}
