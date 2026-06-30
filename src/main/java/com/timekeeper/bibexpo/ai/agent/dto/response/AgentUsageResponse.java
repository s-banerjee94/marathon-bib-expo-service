package com.timekeeper.bibexpo.ai.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * The signed-in user's AI token budget for today (GET {@code /usage}); powers a quota meter.
 *
 * <p>A cap of {@code -1} (with {@code remaining} also {@code -1}) means the caller's role has no
 * configured limit — the UI should treat that as "unlimited" and hide the meter.
 */
@Data
@Builder
@Schema(description = "The caller's daily AI token budget: how much is used, the cap, and when it resets.")
public class AgentUsageResponse {

    @Schema(description = "Tokens used so far today (UTC day).", example = "12500")
    private long used;

    @Schema(description = "Daily token cap for the caller's role. -1 means no cap (unlimited).", example = "100000")
    private long limit;

    @Schema(description = "Tokens left today (limit - used, never below 0). -1 when there is no cap.", example = "87500")
    private long remaining;

    @Schema(description = "When the budget resets — the next UTC midnight, as an ISO-8601 instant.",
            example = "2026-06-29T00:00:00Z")
    private Instant resetsAt;
}
