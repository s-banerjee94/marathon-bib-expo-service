package com.timekeeper.bibexpo.passwordreset.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.passwordreset.exception.PasswordResetInvalidException;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.CompletePasswordResetRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.ForgotPasswordRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.response.PasswordResetTokenStatusResponse;
import com.timekeeper.bibexpo.passwordreset.service.PasswordResetService;
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
        log.info("Public forgot-password request received");
        passwordResetService.requestReset(request);
        // Always the same response, so account existence is never disclosed.
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<PasswordResetTokenStatusResponse> validateToken(String token) {
        log.info("Public request to validate a password reset token");
        return ResponseEntity.ok(passwordResetService.validate(token));
    }

    @Override
    public ResponseEntity<Void> completeReset(String token, CompletePasswordResetRequest request) {
        log.info("Public request to complete a password reset");
        passwordResetService.completeReset(token, request);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(PasswordResetInvalidException.class)
    public ResponseEntity<ErrorResponse> handleResetInvalid(PasswordResetInvalidException ex, WebRequest request) {
        log.warn("Password reset rejected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }
}
