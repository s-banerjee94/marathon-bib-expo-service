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
@Schema(description = "Paginated list of import jobs")
public class ImportJobListResponse {

    @Schema(description = "List of import jobs")
    private List<ImportJobResponse> imports;

    @Schema(description = "Total number of imports returned", example = "10")
    private Integer totalCount;

    @Schema(description = "Current page number", example = "0")
    private Integer currentPage;

    @Schema(description = "Page size", example = "20")
    private Integer pageSize;

    @Schema(description = "Total number of pages", example = "5")
    private Integer totalPages;
}
