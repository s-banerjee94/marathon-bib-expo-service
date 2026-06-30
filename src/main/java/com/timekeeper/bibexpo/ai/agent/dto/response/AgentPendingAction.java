package com.timekeeper.bibexpo.ai.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * One write action the agent has paused on, awaiting the user's decision. Appears inside
 * {@link AgentChatResponse#getPending()} (status {@code needs_approval}). Documentation-only: the
 * live response is relayed verbatim from the Python agent, so this type exists purely to describe
 * that JSON in the OpenAPI schema.
 *
 * <p>The frontend renders {@code summary} (markdown) and shows a button per {@code actions} entry;
 * it does not need to parse {@code args} to display the request.
 */
@Data
@Schema(description = "A write action awaiting approval: a markdown summary to show, plus the allowed decision buttons.")
public class AgentPendingAction {

    @Schema(description = "Stable handle for this action within the turn.", example = "action-0")
    private String id;

    @Schema(description = "Tool the agent wants to run.", example = "create_user")
    private String name;

    @Schema(description = "Markdown summary of the action to display to the user.",
            example = "**Create User** — please review and confirm:\n- **username**: bob\n- **role**: ORGANIZER_ADMIN")
    private String summary;

    @Schema(description = "Proposed tool arguments (sensitive values masked); shown so the user can see what to confirm or change.")
    private Map<String, Object> args;

    @Schema(description = "Decision buttons to render; send the chosen one back via /resume.",
            example = "[\"approve\",\"edit\",\"reject\"]")
    private List<String> actions;
}
