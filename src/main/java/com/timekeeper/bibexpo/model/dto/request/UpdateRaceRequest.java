package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for updating an existing race")
public class UpdateRaceRequest {

    @Size(min = 2, max = 200, message = "Race name must be between 2 and 200 characters")
    @Schema(description = "Race name", example = "Full Marathon")
    private String raceName;

    @Schema(description = "Race description", example = "42.195 km race for experienced runners")
    private String raceDescription;

    @DecimalMin(value = "0.1", message = "Distance must be at least 0.1 km")
    @Schema(description = "Race distance in kilometers", example = "42.195")
    private Double distanceKm;

    @Schema(description = "Race start time", example = "06:00:00")
    private LocalTime startTime;

    @Schema(description = "Race cut-off time (must be after start time)", example = "12:00:00")
    private LocalTime cutOffTime;

    @AssertTrue(message = "Start time must be before cut-off time")
    public boolean isStartTimeBeforeCutOffTime() {
        if (startTime == null || cutOffTime == null) {
            return true;
        }
        return startTime.isBefore(cutOffTime);
    }
}
