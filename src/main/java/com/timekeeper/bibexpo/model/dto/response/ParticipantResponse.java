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

    @Schema(description = "Timestamp when bib was collected", example = "2024-01-10T10:30:00")
    private String bibCollectedAt;

    @Schema(description = "Creation timestamp", example = "2024-01-10T10:30:00")
    private String createdAt;

    @Schema(description = "Last update timestamp", example = "2024-01-10T10:30:00")
    private String updatedAt;
}
