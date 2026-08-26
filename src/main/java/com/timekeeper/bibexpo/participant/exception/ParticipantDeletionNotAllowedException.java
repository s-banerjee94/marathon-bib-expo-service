package com.timekeeper.bibexpo.participant.exception;

public class ParticipantDeletionNotAllowedException extends RuntimeException {
    public ParticipantDeletionNotAllowedException(String message) {
        super(message);
    }
}
