package com.timekeeper.bibexpo.passwordreset.controller;

import com.timekeeper.bibexpo.passwordreset.exception.PasswordResetInvalidException;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.CompletePasswordResetRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.ForgotPasswordRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.response.PasswordResetTokenStatusResponse;
import com.timekeeper.bibexpo.passwordreset.service.PasswordResetService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PublicPasswordResetController implements PublicPasswordResetControllerApi {

    private final PasswordResetService passwordResetService;

    @Override
    public ResponseEntity<Void> forgotPassword(ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
        // Always the same response, so account existence is never disclosed.
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<PasswordResetTokenStatusResponse> validateToken(String token) {
        return ResponseEntity.ok(passwordResetService.validate(token));
    }

    @Override
    public ResponseEntity<Void> completeReset(String token, CompletePasswordResetRequest request) {
        passwordResetService.completeReset(token, request);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(PasswordResetInvalidException.class)
    public ResponseEntity<ErrorResponse> handleResetInvalid(PasswordResetInvalidException ex, WebRequest request) {
        log.atWarn()
                .addKeyValue("event", "request.rejected")
                .addKeyValue("status", HttpStatus.NOT_FOUND.value())
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .addKeyValue("reason", ex.getMessage())
                .log("Request rejected");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }
}
