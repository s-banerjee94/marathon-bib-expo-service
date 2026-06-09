package com.timekeeper.bibexpo.billing.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Acknowledgement of a manual bill-stats refresh. The recompute runs asynchronously in the Lambda,
 * so this returns immediately with the snapshot's current (pre-refresh) timestamp; the UI re-reads
 * the stats shortly after to pick up the new figures.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Acknowledgement that a bill-stats recompute was triggered")
public class BillStatsRefreshResponse {

    @Schema(description = "The snapshot's timestamp at the moment of the request (before the recompute lands); null if never computed",
            example = "2026-06-08T10:00:00Z", nullable = true)
    private Instant refreshedAt;

    @Schema(description = "Human-readable acknowledgement", example = "Statistics recompute has been triggered.")
    private String message;
}
