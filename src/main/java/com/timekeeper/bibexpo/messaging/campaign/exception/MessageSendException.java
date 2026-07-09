package com.timekeeper.bibexpo.messaging.campaign.exception;

/**
 * Raised by a {@code ParticipantSender} when delivering a single campaign message to one
 * participant fails. The dispatcher counts it toward the consecutive-failure threshold and
 * continues with the next participant rather than aborting the whole run.
 */
public class MessageSendException extends Exception {

    public MessageSendException(String message) {
        super(message);
    }

    public MessageSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
