package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for updating a participant")
public class UpdateParticipantRequest {

    @Size(max = 50, message = "Chip number cannot exceed 50 characters")
    @Schema(description = "Participant chip number (must be unique within the event when set to a non-blank value)", example = "0749147")
    private String chipNumber;

    @Size(min = 2, max = 200, message = "Full name must be between 2 and 200 characters")
    @Schema(description = "Participant full name", example = "AMIT KUMAR SARAF")
    private String fullName;

    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    @Schema(description = "Email address", example = "amit@example.com")
    private String email;

    @Pattern(regexp = "^(\\d{10})?$", message = "must be a 10-digit number")
    @Schema(description = "Phone number (10 digits)", example = "9434739700")
    private String phoneNumber;

    @Schema(description = "Date of birth (dd-mm-yyyy)", example = "21-07-1984")
    private String dateOfBirth;

    @Min(value = 0, message = "Age must be non-negative")
    @Max(value = 150, message = "Age must be reasonable")
    @Schema(description = "Participant age", example = "41")
    private Integer age;

    @Pattern(regexp = "^[MFO]$", message = "Gender must be M, F, or O")
    @Schema(description = "Gender (M/F/O)", example = "M")
    private String gender;

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    @Schema(description = "Country", example = "INDIA")
    private String country;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    @Schema(description = "City", example = "RANIGANJ")
    private String city;

    @Schema(description = "Race ID (must exist in event)", example = "3")
    private String raceId;

    @Schema(description = "Category ID (must exist in race)", example = "35")
    private String categoryId;

    @Schema(description = "New BIB number (WARNING: Changes require delete+create in DynamoDB)", example = "21002")
    private String newBibNumber;

    @Size(max = 200, message = "Emergency contact name cannot exceed 200 characters")
    @Schema(description = "Emergency contact name", example = "Jane Doe")
    private String emergencyContactName;

    @Schema(description = "Emergency contact phone", example = "9876543210")
    @Pattern(regexp = "^(\\d{10})?$", message = "must be a 10-digit number")
    private String emergencyContactPhone;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    @Schema(description = "Additional notes", example = "Special accommodation needed")
    private String notes;

    @Schema(description = "Timestamp when bib was collected (ISO format)", example = "2026-01-14T10:30:00")
    private String bibCollectedAt;

    @Schema(description = """
            Additional free-form columns to merge. Include only the keys you want to change: a non-empty value \
            adds or overwrites that key, a null or empty value removes it, and any key you omit is left unchanged. \
            Omit this object entirely to leave all extra columns untouched.""",
            example = "{\"tShirtSize\": \"M\", \"oldColumn\": null}")
    private Map<String, String> additionalFields;
}
