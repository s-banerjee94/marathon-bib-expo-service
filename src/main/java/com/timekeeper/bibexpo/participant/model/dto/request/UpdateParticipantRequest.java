package com.timekeeper.bibexpo.participant.model.dto.request;

import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
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
    @Schema(description = "Participant chip number (must be unique within the event when set to a non-blank value)", example = "0749147", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String chipNumber;

    @Size(min = 2, max = 200, message = "Full name must be between 2 and 200 characters")
    @Schema(description = "Participant full name", example = "AMIT KUMAR SARAF", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String fullName;

    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    @Schema(description = "Email address", example = "amit@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String email;

    @Pattern(regexp = "^(\\d{10})?$", message = "must be a 10-digit number")
    @Schema(description = "Phone number (10 digits)", example = "9434739700", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String phoneNumber;

    @Size(max = 50, message = "Date of birth cannot exceed 50 characters")
    @Schema(description = "Date of birth (dd-mm-yyyy)", example = "21-07-1984", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String dateOfBirth;

    @Min(value = 0, message = "Age must be non-negative")
    @Max(value = 150, message = "Age must be reasonable")
    @Schema(description = "Participant age", example = "41", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer age;

    @Pattern(regexp = "^[MFO]$", message = "Gender must be M, F, or O")
    @Schema(description = "Gender (M/F/O)", example = "M", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String gender;

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    @Schema(description = "Country", example = "INDIA", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String country;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    @Schema(description = "City", example = "RANIGANJ", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String city;

    @Pattern(regexp = "^\\d*$", message = "Race ID must be a number")
    @Schema(description = "Race ID (must exist in event)", example = "3", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String raceId;

    @Pattern(regexp = "^\\d*$", message = "Category ID must be a number")
    @Schema(description = "Category ID (must exist in race)", example = "35", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String categoryId;

    @Size(max = 50, message = "BIB number cannot exceed 50 characters")
    @Schema(description = "New BIB number (WARNING: Changes require delete+create in DynamoDB)", example = "21002", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String newBibNumber;

    @Size(max = 200, message = "Emergency contact name cannot exceed 200 characters")
    @Schema(description = "Emergency contact name", example = "Jane Doe", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String emergencyContactName;

    @Schema(description = "Emergency contact phone", example = "9876543210", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^(\\d{10})?$", message = "must be a 10-digit number")
    private String emergencyContactPhone;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    @Schema(description = "Additional notes", example = "Special accommodation needed", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String notes;

    @Size(max = 50, message = "Bib collection time cannot exceed 50 characters")
    @Schema(description = "Timestamp when bib was collected (ISO format)", example = "2026-01-14T10:30:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String bibCollectedAt;

    @Schema(description = """
            Additional free-form columns to merge. Include only the keys you want to change: a non-empty value \
            adds or overwrites that key, a null or empty value removes it, and any key you omit is left unchanged. \
            Omit this object entirely to leave all extra columns untouched. After the merge a participant may \
            hold at most 50 additional fields; names are up to 100 characters and values up to 500.""",
            example = "{\"tShirtSize\": \"M\", \"oldColumn\": null}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Map<@Size(max = ParticipantDDB.MAX_ADDITIONAL_FIELD_NAME, message = "An additional field name cannot exceed {max} characters") String,
            @Size(max = ParticipantDDB.MAX_ADDITIONAL_FIELD_VALUE, message = "An additional field value cannot exceed {max} characters") String> additionalFields;
}
