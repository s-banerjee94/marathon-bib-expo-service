package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@Schema(description = "Participant details")
public class ParticipantResponse {

    @Schema(description = "Event ID", example = "1")
    private String eventId;

    @Schema(description = "Bib number (unique per event)", example = "3001")
    private String bibNumber;

    @Schema(description = "Chip number (timing chip)", example = "0781354")
    private String chipNumber;

    @Schema(description = "Full name of the participant", example = "SANJAY SUTAR")
    private String fullName;

    @Schema(description = "Email address", example = "sanjaysutar3745@gmail.com")
    private String email;

    @Schema(description = "Phone number", example = "9051217345")
    private String phoneNumber;

    @Schema(description = "Date of birth", example = "1/21/1975")
    private String dateOfBirth;

    @Schema(description = "Age", example = "50")
    private Integer age;

    @Schema(description = "Gender (M/F/O)", example = "M")
    private String gender;

    @Schema(description = "Country", example = "India")
    private String country;

    @Schema(description = "City", example = "Mumbai")
    private String city;

    @Schema(description = "Race ID", example = "5")
    private String raceId;

    @Schema(description = "Race name", example = "3KM")
    private String raceName;

    @Schema(description = "Category ID", example = "12")
    private String categoryId;

    @Schema(description = "Category name", example = "45 TO 59 3KM MALE")
    private String categoryName;

    @Schema(description = "Goodies allocated with sizes as key-value pairs",
            example = "{\"T-Shirt\": \"L\", \"Cap\": \"M\"}")
    private Map<String, String> goodies;

    @Schema(description = "Timestamp when bib was collected", example = "2026-01-10T10:30:00Z")
    private String bibCollectedAt;

    @Schema(description = "Name of the person who collected the bib", example = "John Doe")
    private String bibCollectedByName;

    @Schema(description = "Phone of the person who collected the bib", example = "9876543210")
    private String bibCollectedByPhone;

    @Schema(description = "Staff who distributed the bib", example = "staff_user_01")
    private String bibDistributedBy;

    @Schema(description = "Goodies distribution status as key-value pairs",
            example = "{\"T-Shirt\": \"Distributed\", \"Cap\": \"Pending\"}")
    private Map<String, String> goodiesDistribution;

    @Schema(description = "Emergency contact name", example = "Jane Doe")
    private String emergencyContactName;

    @Schema(description = "Emergency contact phone", example = "9123456780")
    private String emergencyContactPhone;

    @Schema(description = "Additional notes", example = "VIP participant")
    private String notes;

    @Schema(description = "Creation timestamp", example = "2026-01-10T10:30:00Z")
    private String createdAt;

    @Schema(description = "User who created the record", example = "admin")
    private String createdBy;

    @Schema(description = "Last update timestamp", example = "2026-01-10T10:30:00Z")
    private String updatedAt;

    @Schema(description = "User who last updated the record", example = "admin")
    private String updatedBy;
}
