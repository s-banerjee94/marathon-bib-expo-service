package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.AccountDisabledException;
import com.timekeeper.bibexpo.exception.CsrfValidationException;
import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.exception.InvalidCredentialsException;
import com.timekeeper.bibexpo.model.dto.request.LoginRequest;
import com.timekeeper.bibexpo.model.dto.response.LoginResponse;
import com.timekeeper.bibexpo.model.dto.response.RefreshResponse;
import com.timekeeper.bibexpo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication endpoints for login, refresh, and logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(
            summary = "User login",
            description = "Authenticate with username and password. Returns a short-lived access token in the body " +
                    "and sets refreshToken (HttpOnly) + csrfToken cookies. A new login invalidates any prior session.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Login successful",
                            content = @Content(schema = @Schema(implementation = LoginResponse.class))
                    ),
                    @ApiResponse(responseCode = "401", description = "Authentication failed")
            }
    )
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        LoginResponse response = authService.login(request, httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Rotates the refresh token (cookie) and returns a new access token. " +
                    "Requires the X-CSRF-Token header matching the csrfToken cookie (double-submit).",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Refresh successful",
                            content = @Content(schema = @Schema(implementation = RefreshResponse.class))
                    ),
                    @ApiResponse(responseCode = "401", description = "Refresh token invalid, expired, or reuse detected"),
                    @ApiResponse(responseCode = "403", description = "CSRF token mismatch")
            }
    )
    public ResponseEntity<RefreshResponse> refresh(HttpServletRequest httpRequest,
                                                   HttpServletResponse httpResponse) {
        RefreshResponse response = authService.refresh(httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(
            summary = "User logout",
            description = "Ends the session tied to the refresh cookie and clears the auth cookies. " +
                    "Requires the X-CSRF-Token header matching the csrfToken cookie (double-submit). " +
                    "Does not require a valid access token, so logout succeeds even after it has expired.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Logged out successfully"),
                    @ApiResponse(responseCode = "403", description = "CSRF token mismatch")
            }
    )
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        authService.logout(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    // The real failure reason is logged but never returned, so credential probing learns nothing.
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex, WebRequest request) {
        log.warn("Invalid credentials: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid username or password", request));
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ErrorResponse> handleAccountDisabled(AccountDisabledException ex, WebRequest request) {
        log.warn("Account access rejected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request));
    }

    @ExceptionHandler(CsrfValidationException.class)
    public ResponseEntity<ErrorResponse> handleCsrfValidation(CsrfValidationException ex, WebRequest request) {
        log.warn("CSRF validation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request));
    }
}
