package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.response.AiChatHistoryResponse;
import com.timekeeper.bibexpo.model.dto.response.AiChatResponse;
import com.timekeeper.bibexpo.model.entity.User;

/**
 * In-app conversational assistant. Turns a user's natural-language message into a reply,
 * calling the application's tools on the user's behalf so all actions respect the same
 * role-based access as the REST API.
 */
public interface AiChatService {

    /**
     * Handle one chat turn and return the assistant's reply. Each user has a single ongoing
     * conversation, so prior turns are recalled automatically without the caller tracking state.
     *
     * @param message     the user's message
     * @param currentUser the signed-in user the assistant acts as
     * @return the assistant's natural-language reply
     */
    AiChatResponse chat(String message, User currentUser);

    /**
     * Return one page of the signed-in user's stored conversation, newest turns first, so a client
     * can repaint the transcript and lazily load older turns as the user scrolls up. Each page holds
     * up to {@code limit} turns in chronological order; the response carries a cursor for the next
     * (older) page, or null when the start of the conversation has been reached.
     *
     * @param currentUser the signed-in user whose conversation to load
     * @param cursor      the previous page's next-cursor, or null for the most recent page
     * @param limit       maximum turns to return (defaults applied and capped by the service)
     * @return one page of conversation turns plus the cursor for the next older page
     */
    AiChatHistoryResponse getConversation(User currentUser, Integer cursor, Integer limit);

    /**
     * Clear the signed-in user's conversation history, starting their assistant fresh.
     *
     * @param currentUser the signed-in user whose conversation is reset
     */
    void resetConversation(User currentUser);
}
