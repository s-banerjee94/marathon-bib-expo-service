package com.timekeeper.bibexpo.participantaccess.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Result of bulk short URL generation for an event")
public class ShortUrlGenerationResponse {

    @Schema(description = "Total participants processed", example = "500")
    private int total;

    @Schema(description = "Participants that received a new short URL", example = "490")
    private int generated;

    @Schema(description = "Participants skipped because they already had a short URL", example = "10")
    private int skipped;
}
