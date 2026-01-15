package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response for bulk distribution operations")
public class BulkDistributionResponse {

    @Schema(description = "Number of successful operations", example = "5")
    private Integer successCount;

    @Schema(description = "List of successful bib numbers", example = "[\"3001\", \"3002\", \"3003\"]")
    private List<String> successful;

    @Schema(description = "List of failed operations with reasons")
    private List<FailedOperation> failed;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Failed operation details")
    public static class FailedOperation {

        @Schema(description = "Bib number", example = "3004")
        private String bibNumber;

        @Schema(description = "Item name (for goodies distribution)", example = "T-Shirt")
        private String itemName;

        @Schema(description = "Failure reason", example = "Bib not found")
        private String reason;
    }
}
