package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Event response payload")
public class EventResponse {

    @Schema(description = "Event ID", example = "1")
    private Long id;

    @Schema(description = "Event name", example = "Mumbai Marathon 2024")
    private String eventName;

    @Schema(description = "Event description", example = "Annual marathon event in Mumbai with multiple race categories")
    private String eventDescription;

    @Schema(description = "Event logo URL", example = "https://example.com/logos/mumbai-marathon.png")
    private String logoUrl;

    @Schema(description = "IANA timezone ID for the event location", example = "Asia/Kolkata")
    private String timezone;

    @Schema(description = "Event start date in event timezone (yyyy-MM-dd)", example = "2026-10-15")
    private String eventStartDate;

    @Schema(description = "Event start time in event timezone (HH:mm)", example = "06:00")
    private String eventStartTime;

    @Schema(description = "Event end date in event timezone (yyyy-MM-dd)", example = "2026-10-15")
    private String eventEndDate;

    @Schema(description = "Event end time in event timezone (HH:mm)", example = "13:00")
    private String eventEndTime;

    @Schema(description = "Venue name", example = "Bandra Kurla Complex")
    private String venueName;

    @Schema(description = "Address line 1", example = "Bandra Kurla Complex")
    private String addressLine1;

    @Schema(description = "Address line 2", example = "Near MMRDA Grounds")
    private String addressLine2;

    @Schema(description = "City", example = "Mumbai")
    private String city;

    @Schema(description = "State or Province", example = "Maharashtra")
    private String stateProvince;

    @Schema(description = "Postal code", example = "400051")
    private String postalCode;

    @Schema(description = "Country", example = "India")
    private String country;

    @Schema(description = "Venue latitude", example = "19.0596")
    private Double latitude;

    @Schema(description = "Venue longitude", example = "72.8777")
    private Double longitude;

    @Schema(description = "Event status", example = "DRAFT")
    private EventStatus status;

    @Schema(description = "Organization ID that owns this event", example = "1")
    private Long organizationId;

    @Schema(description = "Event goodies as JSON string", example = "{\"tshirt\": true, \"medal\": true, \"certificate\": true}")
    private String eventGoodies;

    @Schema(description = "Event enabled status", example = "true")
    private Boolean enabled;

    @Schema(description = "Creation timestamp", example = "2026-01-15T10:30:00Z")
    private Instant createdAt;

    @Schema(description = "Last update timestamp", example = "2026-01-15T10:30:00Z")
    private Instant updatedAt;

    @Schema(description = "Created by username", example = "admin")
    private String createdBy;

    @Schema(description = "Last modified by username", example = "admin")
    private String lastModifiedBy;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Factory method to create EventResponse from Event entity
     */
    public static EventResponse fromEntity(Event event) {
        String startDate = null, startTime = null, endDate = null, endTime = null;
        if (event.getTimezone() != null) {
            ZoneId zone = ZoneId.of(event.getTimezone());
            if (event.getEventStartDate() != null) {
                ZonedDateTime zStart = event.getEventStartDate().atZone(zone);
                startDate = zStart.toLocalDate().toString();
                startTime = zStart.format(TIME_FMT);
            }
            if (event.getEventEndDate() != null) {
                ZonedDateTime zEnd = event.getEventEndDate().atZone(zone);
                endDate = zEnd.toLocalDate().toString();
                endTime = zEnd.format(TIME_FMT);
            }
        }
        return EventResponse.builder()
                .id(event.getId())
                .eventName(event.getEventName())
                .eventDescription(event.getEventDescription())
                .logoUrl(event.getLogoUrl())
                .timezone(event.getTimezone())
                .eventStartDate(startDate)
                .eventStartTime(startTime)
                .eventEndDate(endDate)
                .eventEndTime(endTime)
                .venueName(event.getVenueName())
                .addressLine1(event.getAddressLine1())
                .addressLine2(event.getAddressLine2())
                .city(event.getCity())
                .stateProvince(event.getStateProvince())
                .postalCode(event.getPostalCode())
                .country(event.getCountry())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .status(event.getStatus())
                .organizationId(event.getOrganization() != null ? event.getOrganization().getId() : null)
                .eventGoodies(event.getEventGoodies())
                .enabled(event.getEnabled())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .createdBy(event.getCreatedBy())
                .lastModifiedBy(event.getLastModifiedBy())
                .build();
    }
}
