package com.timekeeper.bibexpo.model.dto.response.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Event count for a single city")
public class CityCountDto {

    @Schema(description = "Canonical city name", example = "Mumbai")
    private String city;

    @Schema(description = "Number of events in this city", example = "3")
    private long count;
}
