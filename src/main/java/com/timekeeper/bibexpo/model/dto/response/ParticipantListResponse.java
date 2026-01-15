package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Paginated participant list")
public class ParticipantListResponse {

    @Schema(description = "List of participants")
    private List<ParticipantResponse> participants;

    @Schema(description = "DynamoDB pagination key for next page (base64 encoded)",
            example = "eyJldmVudElkIjoiMSIsImJpYk51bWJlciI6IjMwMjAifQ==")
    private String lastEvaluatedKey;

    @Schema(description = "Number of participants in this response", example = "20")
    private Integer count;

    @Schema(description = "Whether there are more participants to fetch", example = "true")
    private Boolean hasMore;
}
