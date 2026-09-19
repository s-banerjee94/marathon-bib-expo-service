package com.timekeeper.bibexpo.distribution.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@Schema(description = "Result of a bulk bib collect or bulk goodies hand-over; each entry succeeds or fails on its own")
public class BulkDistributionResponse {

    @Schema(description = "Number of entries that succeeded", example = "5")
    private Integer successCount;

    @Schema(description = "The entries that succeeded. Bulk collect lists the bib numbers; bulk goodies lists each "
            + "entry as bib:goody,goody", example = "[\"3001\", \"3002\", \"3003\"]")
    private List<String> successful;

    @Schema(description = "The entries that failed, each with its reason. A failed entry saved nothing.")
    private List<FailedOperation> failed;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "One entry that failed")
    public static class FailedOperation {

        @Schema(description = "Bib number", example = "3004")
        private String bibNumber;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "The goodies the entry asked to hand over; left out when it asked for none",
                example = "[\"Bag\", \"Cap\"]")
        private List<String> itemNames;

        @Schema(description = "Why the entry failed, written to be shown as it is",
                example = "Bag and Cap are neither allocated to this participant nor added to the event by hand.")
        private String reason;
    }
}
