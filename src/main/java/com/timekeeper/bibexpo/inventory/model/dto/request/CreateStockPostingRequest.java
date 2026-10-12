package com.timekeeper.bibexpo.inventory.model.dto.request;

import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "One stock action of many lines, posted all or none")
public class CreateStockPostingRequest {

    @NotNull(message = "Type is required")
    @Schema(description = "What the lines do", example = "RECEIPT", allowableValues = {"RECEIPT", "TRANSFER", "ADJUSTMENT"})
    private MovementType type;

    @Size(max = 100, message = "Reference must be at most 100 characters")
    @Schema(description = "What caused this posting, copied onto every ledger line", example = "PO-114",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String reference;

    @Size(max = 500, message = "Note must be at most 500 characters")
    @Schema(description = "A free-text note kept on the posting", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String note;

    @NotEmpty(message = "Add at least one line")
    @Size(max = 200, message = "A posting can hold at most 200 lines")
    @Valid
    @Schema(description = "The lines, 1 to 200. Which fields a line needs depends on the type.")
    private List<StockPostingLineRequest> lines;
}
