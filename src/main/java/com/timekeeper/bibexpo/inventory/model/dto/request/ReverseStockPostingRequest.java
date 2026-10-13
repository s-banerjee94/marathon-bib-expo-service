package com.timekeeper.bibexpo.inventory.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Why a posting is being undone")
public class ReverseStockPostingRequest {

    @Size(max = 500, message = "Note must be at most 500 characters")
    @Schema(description = "A free-text note kept on the undo", example = "Wrong size received",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String note;
}
