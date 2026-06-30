package com.timekeeper.bibexpo.ai.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * One restorable line of conversation history (GET {@code /history}).
 * Documentation-only: the controller relays the Python agent's JSON verbatim, so this type mirrors
 * that JSON for the OpenAPI schema rather than being deserialized at runtime.
 */
@Data
@Schema(description = "A restorable history line: a user/assistant turn, or a summarization divider.")
public class AgentHistoryMessage {

    @Schema(description = "Who said it.", example = "assistant", allowableValues = {"user", "assistant"})
    private String role;

    @Schema(description = "The message text, in markdown.", example = "3 participants have collected their bib.")
    private String content;

    @Schema(description = "True when this marks where earlier turns were summarized (render a divider).",
            example = "false")
    private boolean summary;
}
