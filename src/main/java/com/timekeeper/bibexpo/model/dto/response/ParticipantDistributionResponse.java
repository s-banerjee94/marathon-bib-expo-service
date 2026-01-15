package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@Schema(description = "Participant details with bib and goodies distribution status")
public class ParticipantDistributionResponse {

    @Schema(description = "Event ID", example = "1")
    private String eventId;

    @Schema(description = "Bib number", example = "3001")
    private String bibNumber;

    @Schema(description = "Full name of the participant", example = "SANJAY SUTAR")
    private String fullName;

    @Schema(description = "Email address", example = "sanjaysutar3745@gmail.com")
    private String email;

    @Schema(description = "Phone number", example = "9051217345")
    private String phoneNumber;

    @Schema(description = "Race name", example = "3KM")
    private String raceName;

    @Schema(description = "Category name", example = "45 TO 59 3KM MALE")
    private String categoryName;

    @Schema(description = "Timestamp when bib was collected", example = "2024-01-15T10:30:00")
    private String bibCollectedAt;

    @Schema(description = "Name of person who collected the bib", example = "John Doe")
    private String bibCollectedByName;

    @Schema(description = "Phone number of person who collected the bib", example = "+919876543210")
    private String bibCollectedByPhone;

    @Schema(description = "Staff member who distributed the bib (format: ID__|__Username)",
            example = "123__|__john_doe")
    private String bibDistributedBy;

    @Schema(description = "Goodies allocated with sizes",
            example = "{\"T-Shirt\": \"L\", \"Cap\": \"M\"}")
    private Map<String, String> goodies;

    @Schema(description = "Goodies distribution status with timestamp and distributor",
            example = "{\"T-Shirt\": \"{\\\"collectedAt\\\":\\\"2024-01-15T10:30:00\\\",\\\"distributedBy\\\":\\\"123__|__john_doe\\\"}\"}")
    private Map<String, String> goodiesDistribution;
}
