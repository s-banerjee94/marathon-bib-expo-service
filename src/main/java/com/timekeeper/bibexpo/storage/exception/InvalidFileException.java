package com.timekeeper.bibexpo.storage.exception;

import com.timekeeper.bibexpo.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidFileException extends ApiException {
    public InvalidFileException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
