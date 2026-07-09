package com.timekeeper.bibexpo.model.dto.response.dashboard;

import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Upcoming event entry for the platform dashboard, including its owning organization")
public class UpcomingEventDto {

    @Schema(description = "Event ID", example = "21")
    private Long id;

    @Schema(description = "Event name", example = "Pune Run Fest")
    private String eventName;

    @Schema(description = "Event start date in the event's timezone (yyyy-MM-dd)", format = "date", example = "2026-06-25")
    private String eventStartDate;

    @Schema(description = "Event start time in the event's timezone (HH:mm); null when no time component", example = "06:00", nullable = true)
    private String eventStartTime;

    @Schema(description = "IANA timezone of the event", example = "Asia/Kolkata")
    private String timezone;

    @Schema(description = "City where the event takes place", example = "Pune", nullable = true)
    private String city;

    @Schema(description = "Current event status", example = "PUBLISHED")
    private EventStatus status;

    @Schema(description = "Owning organization ID", example = "17")
    private Long organizationId;

    @Schema(description = "Owning organization name", example = "Pune Runners Co.")
    private String organizerName;

    /**
     * Builds the DTO, converting the event's start instant to its own timezone so the
     * frontend does no timezone math. Requires the event's organization to be loadable.
     */
    public static UpcomingEventDto fromEntity(Event event) {
        String date = null;
        String time = null;

        if (event.getEventStartDate() != null && event.getTimezone() != null) {
            ZonedDateTime local = event.getEventStartDate().atZone(ZoneId.of(event.getTimezone()));
            date = local.toLocalDate().toString();
            LocalTime localTime = local.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
            if (!localTime.equals(LocalTime.MIDNIGHT)) {
                time = localTime.toString();
            }
        }

        return UpcomingEventDto.builder()
                .id(event.getId())
                .eventName(event.getEventName())
                .eventStartDate(date)
                .eventStartTime(time)
                .timezone(event.getTimezone())
                .city(event.getCity())
                .status(event.getStatus())
                .organizationId(event.getOrganization() != null ? event.getOrganization().getId() : null)
                .organizerName(event.getOrganization() != null ? event.getOrganization().getOrganizerName() : null)
                .build();
    }
}
