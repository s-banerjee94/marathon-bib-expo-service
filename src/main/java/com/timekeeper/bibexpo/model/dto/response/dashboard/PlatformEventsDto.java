package com.timekeeper.bibexpo.model.dto.response.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Events block of the platform dashboard")
public class PlatformEventsDto {

    @Schema(description = "Total events within the selected range", example = "312")
    private long total;

    @Schema(description = "Active events (DRAFT + PUBLISHED) within the selected range", example = "140")
    private long active;

    @Schema(description = "Event counts grouped by status within statusRange (full EventStatus key set)")
    private Map<String, Long> byStatus;

    @Schema(description = "Top N cities by event count within citiesRange, excluding CANCELLED events")
    private List<CityCountDto> byCity;

    @Schema(description = "Distinct cities across non-CANCELLED events within citiesRange", example = "37")
    private int distinctCities;

    @Schema(description = "Up to 4 upcoming PUBLISHED events, soonest first")
    private List<UpcomingEventDto> upcomingList;
}
