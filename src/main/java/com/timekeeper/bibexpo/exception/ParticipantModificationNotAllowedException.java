package com.timekeeper.bibexpo.exception;

public class ParticipantModificationNotAllowedException extends RuntimeException {
    public ParticipantModificationNotAllowedException(String message) {
        super(message);
    }
}
