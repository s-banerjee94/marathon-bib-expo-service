package com.timekeeper.bibexpo.distribution.model.dto.response;

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
@Schema(description = "One page of participants who still have something to collect")
public class PendingParticipantListResponse {

    @Schema(description = "Participants on this page, in bib order")
    private List<PendingParticipant> participants;

    @Schema(description = "Pagination token for next page (null if no more pages)")
    private String lastEvaluatedKey;

    @Schema(description = "Number of participants in this response", example = "25")
    private Integer count;

    @Schema(description = "How many participants of the event still have this to collect, across all pages, read "
            + "from the event's statistics", example = "1230")
    private Long totalPending;

    @Schema(description = "Whether there are more pages available", example = "true")
    private Boolean hasMore;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "A participant who still has something to collect")
    public static class PendingParticipant {

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

        @Schema(description = "When the bib was collected; null while it is still to collect",
                example = "2024-01-15T10:30:00")
        private String bibCollectedAt;

        @Schema(description = "Goodies allocated with sizes",
                example = "{\"T-Shirt\": \"L\", \"Cap\": \"M\"}")
        private Map<String, String> goodies;

        @Schema(description = "Goodies distribution status",
                example = "{\"T-Shirt\": \"{\\\"collectedAt\\\":\\\"2024-01-15T10:30:00\\\",\\\"distributedBy\\\":\\\"123__|__john_doe\\\"}\"}")
        private Map<String, String> goodiesDistribution;

        @Schema(description = "The participant's own goodies still to hand over. A goody added to the event by "
                + "hand never appears here.", example = "[\"Cap\", \"Medal\"]")
        private List<String> pendingItems;
    }
}
