package com.timekeeper.bibexpo.participant.model.dto.request;

import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.participant.validation.ValidCreateParticipant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ValidCreateParticipant
@Schema(description = "Request to create a new participant")
public class CreateParticipantRequest {

    @Size(max = 50, message = "Chip number cannot exceed 50 characters")
    @Schema(description = "Chip number (optional; must be unique within the event when provided)", example = "0784525", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String chipNumber;

    @NotBlank(message = "BIB number is required")
    @Size(max = 50, message = "BIB number cannot exceed 50 characters")
    @Schema(description = "BIB number", example = "21001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bibNumber;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 200, message = "Full name must be between 2 and 200 characters")
    @Schema(description = "Full name of participant", example = "JOHN DOE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @NotNull(message = "Race ID is required")
    @Schema(description = "Race ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long raceId;

    @NotNull(message = "Category ID is required")
    @Schema(description = "Category ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoryId;

    @NotBlank(message = "Gender is required")
    @Pattern(regexp = "^[MFO]$", message = "Gender must be M, F, or O")
    @Schema(description = "Gender (M/F/O)", example = "M", requiredMode = Schema.RequiredMode.REQUIRED)
    private String gender;

    @Schema(description = "Phone number (required if email is not provided)", example = "9876543210", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^\\d{10}$", message = "must be a 10-digit number")
    private String phoneNumber;

    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    @Schema(description = "Email address (required if phone number is not provided)", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String email;

    @Size(max = 50, message = "Date of birth cannot exceed 50 characters")
    @Schema(description = "Date of birth (required if age is not provided)", example = "1990-01-15", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String dateOfBirth;

    @Min(value = 4, message = "The age must be greater than 3.")
    @Max(value = 150, message = "The age must be 150 or less.")
    @Schema(description = "Age (required if date of birth is not provided)", example = "35", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer age;

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    @Schema(description = "Country", example = "India", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String country;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    @Schema(description = "City", example = "Mumbai", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String city;

    @Size(max = 50, message = "Bib collection time cannot exceed 50 characters")
    @Schema(description = "Bib collection timestamp", example = "2024-01-15T10:30:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String bibCollectedAt;

    @Schema(description = """
            What the participant is owed, as goody name to value. Every name must already be on the event's \
            goody list (matched ignoring case and surrounding spaces, and stored under the list's spelling); \
            an unknown name is refused. Values are at most 100 characters.""",
            example = "{\"T-Shirt Size\": \"L\", \"Cap Size\": \"M\"}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Map<@Size(max = 150, message = "A goody name cannot exceed 150 characters") String,
            @Size(max = 100, message = "A goody value cannot exceed 100 characters") String> goodies;

    @Size(max = ParticipantDDB.MAX_ADDITIONAL_FIELDS, message = "You can add at most {max} additional fields")
    @Schema(description = "Additional free-form columns as key-value pairs: at most 50, names up to 100 characters, "
            + "values up to 200.",
            example = "{\"pre-existing disease\": \"stroke\"}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Map<@Size(max = ParticipantDDB.MAX_ADDITIONAL_FIELD_NAME, message = "An additional field name cannot exceed {max} characters") String,
            @Size(max = ParticipantDDB.MAX_ADDITIONAL_FIELD_VALUE, message = "An additional field value cannot exceed {max} characters") String> additionalFields;

    @Size(max = 200, message = "Emergency contact name cannot exceed 200 characters")
    @Schema(description = "Emergency contact name", example = "Jane Doe", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String emergencyContactName;

    @Schema(description = "Emergency contact phone", example = "9876543211", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^\\d{10}$", message = "must be a 10-digit number")
    private String emergencyContactPhone;

    @Size(max = 200, message = "Notes cannot exceed 200 characters")
    @Schema(description = "Additional notes", example = "VIP participant", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String notes;
}
