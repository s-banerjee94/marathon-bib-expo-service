package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated list of audit log entries (newest first)")
public class AuditLogListResponse {

    @Schema(description = "Audit log entries in this page (newest first)")
    private List<AuditLogResponse> items;

    @Schema(description = "Number of entries returned in this page", example = "10")
    private Integer count;

    @Schema(description = "Pagination cursor (base64 encoded). Pass to next request to fetch the next page.",
            example = "eyJvcmdhbml6YXRpb25JZCI6eyJOIjoiMTIifSwiZXZlbnRLZXkiOnsiUyI6IjIwMjYtMDUtMjdUMDk6MzA6MDBaIzI4MzcyOTM4In19")
    private String lastEvaluatedKey;

    @Schema(description = "Whether more entries are available for pagination", example = "true")
    private Boolean hasMore;
}
