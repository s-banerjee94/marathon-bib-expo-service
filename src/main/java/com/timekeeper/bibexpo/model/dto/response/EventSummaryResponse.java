package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.entity.Event;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Event summary response with races and categories")
public class EventSummaryResponse {

    @Schema(description = "Event details")
    private EventResponse event;

    @Schema(description = "List of races with category count")
    private List<RaceSummary> races;

    @Schema(description = "Total number of races")
    private Integer totalRaces;

    @Schema(description = "Total number of categories across all races")
    private Integer totalCategories;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Race summary with category count")
    public static class RaceSummary {
        @Schema(description = "Race ID")
        private Long id;

        @Schema(description = "Race name")
        private String raceName;

        @Schema(description = "Race enabled status")
        private Boolean enabled;

        @Schema(description = "Number of categories in this race")
        private Integer categoryCount;
    }

    public static EventSummaryResponse fromEntity(Event event) {
        List<RaceSummary> raceSummaries = event.getRaces().stream()
                .filter(race -> !race.getDeleted())
                .map(race -> RaceSummary.builder()
                        .id(race.getId())
                        .raceName(race.getRaceName())
                        .enabled(race.getEnabled())
                        .categoryCount(race.getCategories() != null ?
                                race.getCategories().size() : 0)
                        .build())
                .collect(Collectors.toList());

        int totalCategories = raceSummaries.stream()
                .mapToInt(RaceSummary::getCategoryCount)
                .sum();

        return EventSummaryResponse.builder()
                .event(EventResponse.fromEntity(event))
                .races(raceSummaries)
                .totalRaces(raceSummaries.size())
                .totalCategories(totalCategories)
                .build();
    }
}
