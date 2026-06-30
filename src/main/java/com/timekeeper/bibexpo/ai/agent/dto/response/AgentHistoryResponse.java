package com.timekeeper.bibexpo.ai.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * A page of conversation history (GET {@code /history}), newest first across pages.
 * Documentation-only: the controller relays the Python agent's JSON verbatim, so this type mirrors
 * that JSON for the OpenAPI schema rather than being deserialized at runtime.
 */
@Data
@Schema(description = "A page of prior messages (oldest first within the page) plus how to load older ones.")
public class AgentHistoryResponse {

    @Schema(description = "The page's messages, oldest first; prepend them above what is already shown.")
    private List<AgentHistoryMessage> messages;

    @Schema(description = "Pass back as 'cursor' to load the previous page; null when nothing older remains.",
            example = "25")
    private Integer nextCursor;

    @Schema(description = "Whether older messages remain to load.", example = "true")
    private boolean hasMore;
}
