package com.timekeeper.bibexpo.ai.agent.service;

import com.timekeeper.bibexpo.ai.agent.dto.response.AgentUsageResponse;
import com.timekeeper.bibexpo.model.entity.User;

/**
 * Guards the AI agent against per-user daily token over-use.
 *
 * <p>Enforcement only: this service owns the limit <em>values</em> and the pre-check, while the
 * Python agent owns the accounting (it writes the exact token usage after each model call). Both
 * read and write the same DynamoDB usage table.
 */
public interface AiUsageService {

    /**
     * Verify {@code user} still has daily AI token budget left, before an agent call is forwarded.
     *
     * <p>Reads today's usage bucket and compares it to the user's role limit. This is a best-effort
     * pre-check (not atomic with the run), so a user may overshoot by at most one request. A read
     * failure fails open, so a usage-store outage never blocks the assistant.
     *
     * @param user the authenticated user about to call the agent
     * @throws com.timekeeper.bibexpo.ai.agent.exception.AiUsageLimitException
     *         when the user's daily token budget is already exhausted
     */
    void checkAllowed(User user);

    /**
     * Read {@code user}'s AI token budget for today: tokens used, the role cap, what remains, and
     * when it resets. A read-only meter for the UI — it never blocks, even when over budget.
     *
     * @param user the authenticated user
     * @return the current daily usage snapshot
     */
    AgentUsageResponse getUsage(User user);
}
