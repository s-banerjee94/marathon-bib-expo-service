package com.timekeeper.bibexpo.participant.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Adds a goody every participant of the event is owed")
public class AddEventGoodieRequest {

    @NotBlank(message = "Goody name is required")
    @Size(max = 150, message = "Goody name must be at most 150 characters")
    @Schema(description = "The goody name", example = "Sponsor Kit")
    private String name;
}
