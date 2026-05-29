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
@Schema(description = "Events block of the org dashboard")
public class EventsDashboardDto {

    @Schema(description = "Total events matching the selected range", example = "12")
    private long total;

    @Schema(description = "Upcoming PUBLISHED events within the selected range", example = "3")
    private long upcoming;

    @Schema(description = "Event counts grouped by status")
    private Map<String, Long> byStatus;

    @Schema(description = "Top N cities by event count, excluding CANCELLED events")
    private List<CityCountDto> byCity;

    @Schema(description = "Total distinct cities across all non-CANCELLED events in range", example = "5")
    private int distinctCities;

    @Schema(description = "Up to 4 active (PUBLISHED) events, sorted by start date ascending")
    private List<EventListItemDto> active;

    @Schema(description = "Up to 10 most recently created events")
    private List<EventListItemDto> recent;
}
