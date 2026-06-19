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
     * Return the signed-in user's stored conversation so a client can repaint the transcript,
     * for example after a page refresh. Reads the same persisted history the assistant recalls
     * and returns an empty list when there is none.
     *
     * @param currentUser the signed-in user whose conversation to load
     * @return the conversation turns in chronological order
     */
    AiChatHistoryResponse getConversation(User currentUser);

    /**
     * Clear the signed-in user's conversation history, starting their assistant fresh.
     *
     * @param currentUser the signed-in user whose conversation is reset
     */
    void resetConversation(User currentUser);
}
