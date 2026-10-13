package com.timekeeper.bibexpo.inventory.model.dto.response;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryPosting;
import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
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
@Schema(description = "A posting and every balance it changed, so a grid can update without reloading")
public class StockPostingResponse {

    @Schema(description = "Posting ID", example = "57")
    private Long id;

    @Schema(description = "What the posting did; REVERSAL for an undo", example = "RECEIPT")
    private MovementType type;

    @Schema(description = "The balance now at every variant and location the posting changed")
    private List<StockBalanceResponse> balances;

    public static StockPostingResponse fromEntity(InventoryPosting posting, List<StockBalanceResponse> balances) {
        return new StockPostingResponse(posting.getId(), posting.getType(), balances);
    }
}
