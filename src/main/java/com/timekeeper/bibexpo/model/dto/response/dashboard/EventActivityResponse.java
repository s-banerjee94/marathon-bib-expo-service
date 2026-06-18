package com.timekeeper.bibexpo.model.dto.response.dashboard;

import com.timekeeper.bibexpo.model.enums.EventActivityRange;
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
@Schema(description = "Range-scoped bib-collection activity for the event dashboard")
public class EventActivityResponse {

    @Schema(description = "The window this block was computed for")
    private EventActivityRange range;

    @Schema(description = "Bibs collected within the window", example = "1642")
    private long collected;

    @Schema(description = "Collected as a percentage of total registered participants", example = "32.8")
    private double collectedPercentOfTotal;

    @Schema(description = "Average collection rate over the elapsed window")
    private Rate rate;

    @Schema(description = "Busiest hour bucket in the window; null when nothing was collected")
    private Peak peak;

    @Schema(description = "Hourly collection-over-time series")
    private Timeline timeline;

    @Schema(description = "Top distributors by bibs handed out within the window, sorted by count descending")
    private List<DistributorCount> distributors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Average collection rate")
    public static class Rate {
        @Schema(description = "Collected divided by elapsed hours in the window (1 decimal)", example = "312.0")
        private double value;

        @Schema(description = "Rate unit (always PER_HOUR)", example = "PER_HOUR")
        private String unit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Busiest hour bucket in the window")
    public static class Peak {
        @Schema(description = "Start of the peak hour, in the event's time zone", example = "2026-06-15T13:00:00+05:30")
        private String bucketStart;

        @Schema(description = "Bibs collected during the peak hour", example = "340")
        private long count;

        @Schema(description = "1-based expo day the peak hour falls on", example = "2")
        private int dayIndex;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Collection-over-time series")
    public static class Timeline {
        @Schema(description = "Bucket interval (always HOUR)", example = "HOUR")
        private String interval;

        @Schema(description = "One series per window; a 'primary' series always, plus a 'compare' series for TODAY")
        private List<Series> series;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "A single timeline series")
    public static class Series {
        @Schema(description = "Series key: 'primary' (the window) or 'compare' (the prior day, TODAY only)", example = "primary")
        private String key;

        @Schema(description = "Human label for the series", example = "Today (15 Jun)")
        private String label;

        @Schema(description = "Hourly points; empty when there is no prior day to compare")
        private List<Point> points;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "A single hourly point")
    public static class Point {
        @Schema(description = "Start of the hour, in the event's time zone", example = "2026-06-15T09:00:00+05:30")
        private String bucketStart;

        @Schema(description = "1-based expo day this hour falls on", example = "2")
        private int dayIndex;

        @Schema(description = "Bibs collected in this hour", example = "150")
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Bibs handed out by a single distributor")
    public static class DistributorCount {
        @Schema(description = "Distributor (staff) user id", example = "88")
        private String distributorId;

        @Schema(description = "Distributor display name", example = "Sourav Ganguly")
        private String name;

        @Schema(description = "Bibs handed out within the window", example = "318")
        private long count;
    }
}
