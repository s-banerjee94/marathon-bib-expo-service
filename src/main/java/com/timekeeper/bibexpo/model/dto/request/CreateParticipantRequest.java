package com.timekeeper.bibexpo.model.dto.request;

import com.timekeeper.bibexpo.validator.ValidCreateParticipant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @Schema(description = "Chip number (optional; must be unique within the event when provided)", example = "0784525", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String chipNumber;

    @NotBlank(message = "BIB number is required")
    @Schema(description = "BIB number", example = "21001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bibNumber;

    @NotBlank(message = "Full name is required")
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

    @Schema(description = "Email address (required if phone number is not provided)", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String email;

    @Schema(description = "Date of birth (required if age is not provided)", example = "1990-01-15", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String dateOfBirth;

    @Schema(description = "Age (required if date of birth is not provided)", example = "35", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer age;

    @Schema(description = "Country", example = "India", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String country;

    @Schema(description = "City", example = "Mumbai", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String city;

    @Schema(description = "Bib collection timestamp", example = "2024-01-15T10:30:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String bibCollectedAt;

    @Schema(description = "Goodies map (key-value pairs)", example = "{\"T-Shirt Size\": \"L\", \"Cap Size\": \"M\"}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Map<String, String> goodies;

    @Schema(description = "Additional free-form columns as key-value pairs",
            example = "{\"pre-existing disease\": \"stroke\"}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Map<String, String> additionalFields;

    @Schema(description = "Emergency contact name", example = "Jane Doe", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String emergencyContactName;

    @Schema(description = "Emergency contact phone", example = "9876543211", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^\\d{10}$", message = "must be a 10-digit number")
    private String emergencyContactPhone;

    @Schema(description = "Additional notes", example = "VIP participant", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String notes;
}
