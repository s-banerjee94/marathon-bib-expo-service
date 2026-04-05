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
@Schema(description = "Paginated list of import errors")
public class ImportErrorListResponse {

    @Schema(description = "Import job ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String importId;

    @Schema(description = "List of errors for this page")
    private List<ImportError> errors;

    @Schema(description = "Number of errors returned in this response", example = "50")
    private Integer count;

    @Schema(description = "Pagination token for next page (base64 encoded). Use this value in the next request to get the next page.",
            example = "eyJpbXBvcnRJZCI6eyJTIjoiNTUwZTg0MDAtZTI5Yi00MWQ0LWE3MTYtNDQ2NjU1NDQwMDAwIn0sImVycm9ySWQiOnsiUyI6ImVycjEyMyJ9fQ==")
    private String lastEvaluatedKey;

    @Schema(description = "Whether more errors are available for pagination", example = "true")
    private Boolean hasMore;

}
