package com.timekeeper.bibexpo.model.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for creating a new event")
public class CreateEventRequest {

    @NotBlank(message = "Event name is required")
    @Size(min = 2, max = 200, message = "Event name must be between 2 and 200 characters")
    @Schema(description = "Event name", example = "Mumbai Marathon 2024")
    private String eventName;

    @Schema(description = "Event description", example = "Annual marathon event in Mumbai with multiple race categories")
    private String eventDescription;

    @Schema(description = "Event logo URL", example = "https://example.com/logos/mumbai-marathon.png")
    private String logoUrl;

    @NotBlank(message = "Timezone is required")
    @Schema(description = "IANA timezone ID for the event location", example = "Asia/Kolkata")
    private String timezone;

    @NotNull(message = "Event start date is required")
    @Future(message = "Event start date must be in the future")
    @Schema(description = "Event start date and time", example = "2024-01-15T06:00:00")
    private LocalDateTime eventStartDate;

    @NotNull(message = "Event end date is required")
    @Schema(description = "Event end date and time", example = "2024-01-15T12:00:00")
    private LocalDateTime eventEndDate;

    @NotBlank(message = "Venue name is required")
    @Size(min = 2, max = 200, message = "Venue name must be between 2 and 200 characters")
    @Schema(description = "Venue name", example = "Bandra Kurla Complex")
    private String venueName;

    @Size(max = 200, message = "Address line 1 must not exceed 200 characters")
    @Schema(description = "Address line 1", example = "Bandra Kurla Complex")
    private String addressLine1;

    @Size(max = 200, message = "Address line 2 must not exceed 200 characters")
    @Schema(description = "Address line 2", example = "Near MMRDA Grounds")
    private String addressLine2;

    @Size(max = 100, message = "City must not exceed 100 characters")
    @Schema(description = "City", example = "Mumbai")
    private String city;

    @Size(max = 100, message = "State/Province must not exceed 100 characters")
    @Schema(description = "State or Province", example = "Maharashtra")
    private String stateProvince;

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    @Schema(description = "Postal code", example = "400051")
    private String postalCode;

    @Size(max = 100, message = "Country must not exceed 100 characters")
    @Schema(description = "Country", example = "India")
    private String country;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    @Schema(description = "Venue latitude", example = "19.0596")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    @Schema(description = "Venue longitude", example = "72.8777")
    private Double longitude;

    @Schema(description = "Event status", example = "DRAFT", allowableValues = {"DRAFT", "PUBLISHED", "CANCELLED", "COMPLETED"}, defaultValue = "DRAFT")
    private EventStatus status;

    @NotNull(message = "Organization ID is required")
    @Schema(description = "Organization ID that owns this event", example = "1")
    private Long organizationId;

    @Schema(description = "Event goodies as JSON string", example = "{\"tshirt\": true, \"medal\": true, \"certificate\": true}")
    private String eventGoodies;

    @JsonIgnore
    @AssertTrue(message = "Event end date must be after start date")
    public boolean isEventDateValid() {
        if (eventStartDate == null || eventEndDate == null) {
            return true;
        }
        return eventEndDate.isAfter(eventStartDate);
    }
}
