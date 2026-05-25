package com.timekeeper.bibexpo.participantaccess.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "Participant details for self-verification before distribution")
public class ParticipantVerificationResponse {

    @Schema(description = "Event name", example = "Mumbai Marathon 2025")
    private String eventName;

    @Schema(description = "Event venue", example = "Azad Maidan, Mumbai")
    private String eventVanue;

    @Schema(description = "Event start date and time (UTC)", example = "2025-01-19T03:00:00Z")
    private Instant eventStartDate;

    @Schema(description = "Event end date and time (UTC)", example = "2025-01-19T10:00:00Z")
    private Instant eventEndDate;

    @Schema(description = "Bib number", example = "3001")
    private String bibNumber;

    @Schema(description = "Chip number", example = "0781354")
    private String chipNumber;

    @Schema(description = "Full name", example = "SANJAY SUTAR")
    private String fullName;

    @Schema(description = "Email address", example = "sanjay@example.com")
    private String email;

    @Schema(description = "Phone number", example = "9051217345")
    private String phoneNumber;

    @Schema(description = "Race name", example = "21KM")
    private String raceName;

    @Schema(description = "Category name", example = "45 TO 59 21KM MALE")
    private String categoryName;

    @Schema(description = "Age", example = "50")
    private Integer age;

    @Schema(description = "Gender", example = "M")
    private String gender;

    @Schema(description = "City", example = "Mumbai")
    private String city;

    @Schema(description = "Country", example = "India")
    private String country;

    @Schema(description = "Emergency contact full name", example = "Priya Sutar")
    private String emergencyContactName;

    @Schema(description = "Emergency contact phone number", example = "9876543210")
    private String emergencyContactPhone;

    @Schema(description = "Compact QR code as a PNG data URI; render upscaled with image-rendering: pixelated",
            example = "data:image/png;base64,iVBORw0KGgoAAAANS...")
    private String qrCodeDataUri;
}
