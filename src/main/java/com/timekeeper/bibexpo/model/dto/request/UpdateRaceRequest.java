package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for updating an existing race")
public class UpdateRaceRequest {

    @Size(min = 2, max = 200, message = "Race name must be between 2 and 200 characters")
    @Schema(description = "Race name", example = "Full Marathon", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String raceName;

    @Schema(description = "Race description", example = "42.195 km race for experienced runners", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String raceDescription;

    @Schema(description = "Reporting time for participants (UTC instant, interpreted in event timezone)", example = "2026-06-15T01:30:00Z", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant reportingTime;
}
