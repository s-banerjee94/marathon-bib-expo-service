package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Paginated distribution log list")
public class DistributionLogListResponse {

    @Schema(description = "List of distribution logs")
    private List<DistributionLogResponse> logs;

    @Schema(description = "DynamoDB pagination key for next page (base64 encoded)",
            example = "eyJldmVudElkIjoiMSIsInRpbWVzdGFtcCI6IjIwMjQtMDEtMTVUMTA6MzA6MDAifQ==")
    private String lastEvaluatedKey;

    @Schema(description = "Number of logs in this response", example = "20")
    private Integer count;

    @Schema(description = "Whether there are more logs to fetch", example = "true")
    private Boolean hasMore;
}
