package com.timekeeper.bibexpo.exception;

public class ParticipantDeletionNotAllowedException extends RuntimeException {
    public ParticipantDeletionNotAllowedException(String message) {
        super(message);
    }
}
