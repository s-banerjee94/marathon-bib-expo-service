package com.timekeeper.bibexpo.ai.agent.exception;

/**
 * Thrown when the Python AI agent cannot be reached or returns an error. Surfaced to the
 * caller as a 503 by the chat controller's local exception handler.
 */
public class AiAgentException extends RuntimeException {

    public AiAgentException(String message) {
        super(message);
    }
}
