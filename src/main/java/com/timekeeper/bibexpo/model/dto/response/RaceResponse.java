package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.Race;
import com.timekeeper.bibexpo.util.EventDateTimeUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Race response payload")
public class RaceResponse {

    @Schema(description = "Race ID", example = "1")
    private Long id;

    @Schema(description = "Race name", example = "Full Marathon")
    private String raceName;

    @Schema(description = "Race description", example = "42.195 km race for experienced runners")
    private String raceDescription;

    @Schema(description = "Event ID this race belongs to", example = "1")
    private Long eventId;

    @Schema(description = "Organization ID that owns the parent event", example = "1")
    private Long organizationId;

    @Schema(description = "Number of categories in this race", example = "3")
    private Integer categoryCount;

    @Schema(description = "Creation timestamp", example = "2026-01-15T10:30:00Z")
    private Instant createdAt;

    @Schema(description = "Race-day reporting date in the event's local timezone (yyyy-MM-dd), null if not set", example = "2026-10-26")
    private String reportingDate;

    @Schema(description = "Race-day reporting time in the event's local timezone (HH:mm), null if not set", example = "04:00")
    private String reportingTime;

    @Schema(description = "Last update timestamp", example = "2026-01-15T10:30:00Z")
    private Instant updatedAt;

    @Schema(description = "Created by username", example = "admin")
    private String createdBy;

    @Schema(description = "Last modified by username", example = "admin")
    private String lastModifiedBy;

    public static RaceResponse fromEntity(Race race) {
        String reportingDate = null, reportingTime = null;
        Event event = race.getEvent();
        if (event != null && event.getTimezone() != null && race.getReportingTime() != null) {
            ZoneId zone = ZoneId.of(event.getTimezone());
            reportingDate = EventDateTimeUtil.dateOf(race.getReportingTime(), zone);
            reportingTime = EventDateTimeUtil.timeOf(race.getReportingTime(), zone);
        }
        return RaceResponse.builder()
                .id(race.getId())
                .raceName(race.getRaceName())
                .raceDescription(race.getRaceDescription())
                .eventId(event != null ? event.getId() : null)
                .organizationId(event != null && event.getOrganization() != null
                        ? event.getOrganization().getId() : null)
                .categoryCount(race.getCategories() != null ? race.getCategories().size() : 0)
                .reportingDate(reportingDate)
                .reportingTime(reportingTime)
                .createdAt(race.getCreatedAt())
                .updatedAt(race.getUpdatedAt())
                .createdBy(race.getCreatedBy())
                .lastModifiedBy(race.getLastModifiedBy())
                .build();
    }
}
