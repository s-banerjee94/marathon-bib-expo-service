package com.timekeeper.bibexpo.event.race.exception;

public class RaceAlreadyExistsException extends RuntimeException {
    public RaceAlreadyExistsException(String message) {
        super(message);
    }
}
