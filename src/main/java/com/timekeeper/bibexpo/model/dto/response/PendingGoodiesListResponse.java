package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Paginated response for participants with pending goodies items")
public class PendingGoodiesListResponse {

    @Schema(description = "List of participants with pending goodies")
    private List<ParticipantPendingGoodies> participants;

    @Schema(description = "Pagination token for next page (null if no more pages)")
    private String lastEvaluatedKey;

    @Schema(description = "Number of participants in this response", example = "25")
    private Integer count;

    @Schema(description = "Whether there are more pages available", example = "true")
    private Boolean hasMore;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Participant with pending goodies details")
    public static class ParticipantPendingGoodies {

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

        @Schema(description = "Goodies allocated with sizes",
                example = "{\"T-Shirt\": \"L\", \"Cap\": \"M\"}")
        private Map<String, String> goodies;

        @Schema(description = "Goodies distribution status",
                example = "{\"T-Shirt\": \"{\\\"collectedAt\\\":\\\"2024-01-15T10:30:00\\\",\\\"distributedBy\\\":\\\"123__|__john_doe\\\"}\"}")
        private Map<String, String> goodiesDistribution;

        @Schema(description = "List of pending goodies item names",
                example = "[\"Cap\", \"Medal\"]")
        private List<String> pendingItems;
    }
}
