package com.timekeeper.bibexpo.ai.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Shape of an agent reply (POST {@code /chat} and {@code /resume}).
 * Documentation-only: the controller relays the Python agent's JSON verbatim, so this type mirrors
 * that JSON for the OpenAPI schema rather than being deserialized at runtime.
 *
 * <p>All four fields are always present. The two shapes are distinguished by {@code status}:
 * <ul>
 *   <li>{@code complete} — {@code reply} holds the assistant's markdown answer; {@code pending} is {@code []}.</li>
 *   <li>{@code needs_approval} — {@code pending} lists the writes awaiting a decision; {@code reply}
 *       is {@code ""}. Send one decision per pending action to {@code /resume} to continue.</li>
 * </ul>
 */
@Data
@Schema(description = "A final reply (status=complete) or a request to approve pending writes (status=needs_approval).")
public class AgentChatResponse {

    @Schema(description = "complete = final answer in 'reply'; needs_approval = decisions required for 'pending'.",
            example = "complete", allowableValues = {"complete", "needs_approval"})
    private String status;

    @Schema(description = "The assistant's answer, in markdown. Non-empty when status is complete, \"\" otherwise.",
            example = "3 participants have collected their bib.")
    private String reply;

    @Schema(description = "Writes awaiting approval, in the order decisions must be returned. "
            + "Empty array when status is complete.")
    private List<AgentPendingAction> pending;

    @Schema(description = "Conversation thread id (always user-<id>); the server keeps one conversation per user.",
            example = "user-42")
    private String threadId;
}
