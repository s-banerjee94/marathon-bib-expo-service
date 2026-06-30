package com.timekeeper.bibexpo.ai.agent.service;

import com.timekeeper.bibexpo.ai.agent.dto.request.AgentDecision;
import com.timekeeper.bibexpo.model.entity.User;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Calls the Python LangGraph agent on behalf of an authenticated user.
 *
 * <p>Each call mints a fresh, short-lived MCP-scoped token for {@code user} and forwards it to the
 * agent, so the agent acts under that user's identity and role without ever holding a browser
 * session token. Responses are relayed verbatim from the agent (a final reply, or a pending-approval
 * request, plus the conversation's thread id).
 */
public interface AiAgentClient {

    /**
     * Send one chat message for {@code user}.
     *
     * @param user    the authenticated user the agent acts on behalf of
     * @param message the user's message
     * @param mode    optional approval-mode override ({@code auto}, {@code agent} or {@code ask}); may be null
     * @return the agent's JSON response
     */
    JsonNode chat(User user, String message, String mode);

    /**
     * Resume a conversation paused for human approval, supplying one decision per pending action.
     *
     * @param user      the authenticated user the agent acts on behalf of
     * @param decisions the human decisions, in the order the pending actions were returned
     * @param mode      the approval mode the conversation is using ({@code auto}, {@code agent} or {@code ask})
     * @return the agent's JSON response
     */
    JsonNode resume(User user, List<AgentDecision> decisions, String mode);

    /**
     * Delete {@code user}'s conversation memory so their next message starts with empty history.
     *
     * <p>Reaches the agent's checkpoint store via the agent service; the user can only ever reset
     * their own conversation, since the thread is derived from the trusted identity, never the client.
     *
     * @param user the authenticated user whose conversation should be reset
     */
    void resetConversation(User user);

    /**
     * Fetch one page of {@code user}'s stored conversation history, newest first (25 per page).
     *
     * @param user   the authenticated user whose history to read
     * @param cursor the previous page's {@code nextCursor}; {@code null} for the newest page
     * @return the agent's JSON response (a page of messages plus {@code nextCursor} / {@code hasMore})
     */
    JsonNode history(User user, Integer cursor);
}
