package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Participant statistics for an event")
public class ParticipantStatisticsResponse {

    @Schema(description = "Event ID")
    private Long eventId;

    @Schema(description = "Total number of participants")
    private Integer totalParticipants;

    @Schema(description = "Number of participants who collected their bib")
    private Integer bibCollectedCount;

    @Schema(description = "Number of participants pending bib collection")
    private Integer pendingCount;

    @Schema(description = "Statistics breakdown by race")
    private List<RaceStatistics> raceBreakdown;

    @Schema(description = "Statistics breakdown by category")
    private List<CategoryStatistics> categoryBreakdown;

    @Schema(description = "Statistics breakdown by gender")
    private GenderStatistics genderBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Statistics for a specific race")
    public static class RaceStatistics {
        @Schema(description = "Race ID")
        private String raceId;

        @Schema(description = "Race name")
        private String raceName;

        @Schema(description = "Total participants in this race")
        private Integer count;

        @Schema(description = "Participants who collected their bib")
        private Integer bibCollectedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Statistics for a specific category")
    public static class CategoryStatistics {
        @Schema(description = "Category ID")
        private String categoryId;

        @Schema(description = "Category name")
        private String categoryName;

        @Schema(description = "Total participants in this category")
        private Integer count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Statistics breakdown by gender")
    public static class GenderStatistics {
        @Schema(description = "Number of male participants")
        private Integer male;

        @Schema(description = "Number of female participants")
        private Integer female;

        @Schema(description = "Number of other/unspecified gender participants")
        private Integer other;
    }
}
