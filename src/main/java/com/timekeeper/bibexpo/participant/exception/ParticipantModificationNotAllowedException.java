package com.timekeeper.bibexpo.participant.exception;

public class ParticipantModificationNotAllowedException extends RuntimeException {
    public ParticipantModificationNotAllowedException(String message) {
        super(message);
    }
}
