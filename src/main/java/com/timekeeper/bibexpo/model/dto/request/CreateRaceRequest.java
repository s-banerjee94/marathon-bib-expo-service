package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for creating a new race")
public class CreateRaceRequest {

    @NotBlank(message = "Race name is required")
    @Size(min = 2, max = 200, message = "Race name must be between 2 and 200 characters")
    @Schema(description = "Race name", example = "Full Marathon")
    private String raceName;

    @Schema(description = "Race description", example = "42.195 km race for experienced runners", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String raceDescription;

    @Schema(description = "Race-day reporting date in the event's local timezone (yyyy-MM-dd). Send together with reportingTime",
            example = "2026-10-26", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String reportingDate;

    @Schema(description = "Race-day reporting time in the event's local timezone (HH:mm). Must be at least one hour ahead",
            example = "04:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String reportingTime;
}
