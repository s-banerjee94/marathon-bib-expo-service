package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
