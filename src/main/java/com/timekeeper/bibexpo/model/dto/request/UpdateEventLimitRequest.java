package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "Partial update for per-event resource limits. Only provided fields are updated.")
public class UpdateEventLimitRequest {

    @Min(1)
    @Schema(description = "Maximum number of participants", example = "50000")
    private Integer maxParticipants;

    @Min(1)
    @Schema(description = "Maximum number of races", example = "20")
    private Integer maxRaces;

    @Min(1)
    @Schema(description = "Maximum number of categories per race", example = "50")
    private Integer maxCategoriesPerRace;

    @Min(1)
    @Schema(description = "Maximum number of goodies types", example = "15")
    private Integer maxGoodies;

    @Min(1)
    @Schema(description = "Maximum number of SMS templates", example = "20")
    private Integer maxSmsTemplates;

    @Min(1)
    @Schema(description = "Maximum number of SMS campaigns", example = "20")
    private Integer maxSmsCampaigns;

    @Min(1)
    @Schema(description = "Maximum number of full imports", example = "10")
    private Integer maxImports;

    @Min(1)
    @Schema(description = "Maximum number of add-on imports", example = "5")
    private Integer maxAddOns;
}
