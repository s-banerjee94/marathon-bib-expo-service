package com.timekeeper.bibexpo.exception;

public class ParticipantNotFoundException extends RuntimeException {

    public ParticipantNotFoundException(String eventId, String bibNumber) {
        super(String.format("Participant with bib number '%s' not found for event '%s'", bibNumber, eventId));
    }

    public ParticipantNotFoundException(String message) {
        super(message);
    }
}
