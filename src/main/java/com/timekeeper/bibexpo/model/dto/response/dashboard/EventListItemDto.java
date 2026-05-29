package com.timekeeper.bibexpo.model.dto.response.dashboard;

import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lightweight event entry for dashboard lists")
public class EventListItemDto {

    @Schema(description = "Event ID", example = "17")
    private Long id;

    @Schema(description = "Event name", example = "Pune Run Fest")
    private String eventName;

    @Schema(description = "Event start date in the event's timezone (yyyy-MM-dd)", format = "date", example = "2026-10-15")
    private String eventStartDate;

    @Schema(description = "Event start time in the event's timezone (HH:mm); null when no time component is recorded", example = "06:00", nullable = true)
    private String eventStartTime;

    @Schema(description = "IANA timezone of the event", example = "Asia/Kolkata")
    private String timezone;

    @Schema(description = "City where the event takes place", example = "Pune")
    private String city;

    @Schema(description = "Current event status", example = "PUBLISHED")
    private EventStatus status;

    @Schema(description = "Event logo URL", example = "https://cdn.example.com/logo.png")
    private String logoUrl;

    public static EventListItemDto fromEntity(Event event) {
        String date = null;
        String time = null;

        if (event.getEventStartDate() != null && event.getTimezone() != null) {
            ZonedDateTime local = event.getEventStartDate().atZone(ZoneId.of(event.getTimezone()));
            date = local.toLocalDate().toString();
            var localTime = local.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
            if (!localTime.equals(java.time.LocalTime.MIDNIGHT)) {
                time = localTime.toString();
            }
        }

        return EventListItemDto.builder()
                .id(event.getId())
                .eventName(event.getEventName())
                .eventStartDate(date)
                .eventStartTime(time)
                .timezone(event.getTimezone())
                .city(event.getCity())
                .status(event.getStatus())
                .logoUrl(event.getLogoUrl())
                .build();
    }
}
