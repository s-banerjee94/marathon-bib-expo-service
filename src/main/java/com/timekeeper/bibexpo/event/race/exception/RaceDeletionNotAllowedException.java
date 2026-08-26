package com.timekeeper.bibexpo.event.race.exception;

public class RaceDeletionNotAllowedException extends RuntimeException {
    public RaceDeletionNotAllowedException(String message) {
        super(message);
    }
}
