package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to collect bibs for multiple participants with the same collector")
public class BulkCollectBibRequest {

    @NotEmpty(message = "Bib numbers list cannot be empty")
    @Schema(description = "List of bib numbers to collect",
            example = "[\"3001\", \"3002\", \"3003\"]", required = true)
    private List<String> bibNumbers;

    @Schema(description = "Name of person collecting the bibs (defaults to each participant if not provided)",
            example = "John Doe")
    private String collectorName;

    @Schema(description = "Phone number of person collecting the bibs (defaults to each participant if not provided)",
            example = "+919876543210")
    private String collectorPhone;
}
