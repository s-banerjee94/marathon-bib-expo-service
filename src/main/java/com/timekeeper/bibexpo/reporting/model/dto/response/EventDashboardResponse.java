package com.timekeeper.bibexpo.reporting.model.dto.response;

import com.timekeeper.bibexpo.reporting.model.enums.EventActivityRange;
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
@Schema(description = "Single-round-trip rollup for the event-details Dashboard tab")
public class EventDashboardResponse {

    @Schema(description = "When this rollup was computed, in the event's time zone", example = "2026-06-15T14:14:02+05:30")
    private String refreshedAt;

    @Schema(description = "The window the range-scoped activity block was computed for")
    private EventActivityRange range;

    @Schema(description = "Event context for labels and day-boundary logic")
    private EventContext event;

    @Schema(description = "Event-wide participant totals (independent of range)")
    private ParticipantTotals participants;

    @Schema(description = "Event-wide gender breakdown")
    private GenderBreakdown gender;

    @Schema(description = "Per-race totals and collection progress (drives both race cards)")
    private List<RaceStat> races;

    @Schema(description = "Per-category totals, each tagged with its race for client-side filtering")
    private List<CategoryStat> categories;

    @Schema(description = "Range-scoped collection activity")
    private EventActivityResponse activity;

    @Schema(description = "What the imported roster was promised, per goodies column and per "
            + "distinct spelling of its value. Empty for an event imported before these counters "
            + "existed, until its statistics are reconciled")
    private List<GoodieDemandStat> goodies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Event context")
    public static class EventContext {
        @Schema(description = "Event ID", example = "1")
        private Long eventId;

        @Schema(description = "Event IANA time zone", example = "Asia/Kolkata")
        private String timezone;

        @Schema(description = "Expo start, in the event's time zone", example = "2026-06-14T09:00:00+05:30")
        private String expoStart;

        @Schema(description = "Expo end, in the event's time zone", example = "2026-06-15T15:00:00+05:30")
        private String expoEnd;

        @Schema(description = "Number of expo days", example = "2")
        private int dayCount;

        @Schema(description = "1-based expo day 'now' falls on; null when outside the expo window", example = "2")
        private Integer currentDayIndex;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Event-wide participant totals")
    public static class ParticipantTotals {
        @Schema(description = "Total registered participants", example = "5000")
        private long total;

        @Schema(description = "Participants who have collected their bib (cumulative)", example = "3100")
        private long collected;

        @Schema(description = "Participants still pending collection", example = "1900")
        private long pending;

        @Schema(description = "Collected as a percentage of total", example = "62.0")
        private double collectedPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Gender breakdown")
    public static class GenderBreakdown {
        @Schema(description = "Male participants", example = "3050")
        private long male;

        @Schema(description = "Female participants", example = "1820")
        private long female;

        @Schema(description = "Other/unspecified participants", example = "130")
        private long other;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "One goodies column, one spelling of its value, and how many "
            + "participants were promised it")
    public static class GoodieDemandStat {
        @Schema(description = "The goodies column heading, as the import stored it", example = "T-Shirt")
        private String goodieName;

        @Schema(description = "The cell value, exactly as imported; empty when the column held too "
                + "many distinct values to count one by one", example = "M")
        private String value;

        @Schema(description = "Participants promised that value, or, when countedByValue is false, "
                + "how many distinct values the column held", example = "340")
        private long participants;

        @Schema(description = "False when the column has too many distinct values to be a goody at "
                + "all, which means the wrong column was marked as goodies on the import screen",
                example = "true")
        private boolean countedByValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Per-race totals and collection progress")
    public static class RaceStat {
        @Schema(description = "Race ID", example = "11")
        private String raceId;

        @Schema(description = "Race name", example = "Half Marathon")
        private String raceName;

        @Schema(description = "Registered participants in this race", example = "2400")
        private long total;

        @Schema(description = "Bibs collected in this race", example = "1690")
        private long collected;

        @Schema(description = "Collected as a percentage of the race total", example = "70.4")
        private double collectedPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Per-category total, tagged with its race")
    public static class CategoryStat {
        @Schema(description = "Race ID the category belongs to; null when the category no longer exists",
                example = "11")
        private String raceId;

        @Schema(description = "Category ID", example = "101")
        private String categoryId;

        @Schema(description = "Category name, or an 'Unknown category (#id)' placeholder when the "
                + "category no longer exists", example = "Male · 18–35")
        private String categoryName;

        @Schema(description = "Registered participants in this category", example = "820")
        private long total;

        @Schema(description = "Bibs collected in this category", example = "540")
        private long collected;

        @Schema(description = "Collected as a percentage of the category total", example = "65.9")
        private double collectedPercent;
    }
}
