package com.timekeeper.bibexpo.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    public static final String BAD_REQUEST = "Bad Request";
    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String FORBIDDEN = "Forbidden";
    public static final String NOT_FOUND = "Not Found";
    public static final String CONFLICT = "Conflict";
    public static final String METHOD_NOT_ALLOWED = "Method Not Allowed";

    /**
     * Single handler for every cross-cutting {@link ApiException}: the exception itself carries
     * its status and error label. Exceptions owned by exactly one controller are handled by a
     * local {@code @ExceptionHandler} in that controller instead.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, WebRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        } else {
            log.warn("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getStatus(), ex.getError(), ex.getMessage(), request));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        log.error("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(HttpStatus.UNAUTHORIZED, UNAUTHORIZED, "Authentication failed", request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        log.error("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(HttpStatus.FORBIDDEN, FORBIDDEN,
                        "You do not have permission to access this resource", request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, WebRequest request) {
        log.error("Data integrity violation: {}", ex.getMessage());
        return mapPersistenceError(ex.getMessage(), "Database constraint violation", request);
    }

    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<ErrorResponse> handleJpaSystemException(
            JpaSystemException ex, WebRequest request) {
        log.error("JPA system exception: {}", ex.getMessage());
        return mapPersistenceError(ex.getMessage(), "Database error occurred", request);
    }

    /**
     * Translates raw persistence errors (DB triggers, unique constraints) into the user-facing
     * messages the frontend shows verbatim.
     */
    private ResponseEntity<ErrorResponse> mapPersistenceError(
            String exceptionMessage, String defaultMessage, WebRequest request) {
        String message = defaultMessage;
        String error = BAD_REQUEST;
        HttpStatus status = HttpStatus.BAD_REQUEST;

        if (exceptionMessage != null) {
            if (exceptionMessage.contains("Organizer user limit exceeded")) {
                message = "Cannot create organizer user: The organizer has reached the maximum limit of organizer users (default: 5)";
                error = "Organizer User Limit Exceeded";
            } else if (exceptionMessage.contains("Distributor limit exceeded")) {
                message = "Cannot create distributor: The organizer has reached the maximum limit of distributors (default: 30)";
                error = "Distributor Limit Exceeded";
            } else if (exceptionMessage.contains("uk_user_phone") || exceptionMessage.contains("phoneNumber")) {
                message = "Phone number already exists";
                error = CONFLICT;
                status = HttpStatus.CONFLICT;
            } else if (exceptionMessage.contains("uk_user_email")) {
                message = "Email already exists";
                error = CONFLICT;
                status = HttpStatus.CONFLICT;
            } else if (exceptionMessage.contains("uk_user_username")) {
                message = "Username already exists";
                error = CONFLICT;
                status = HttpStatus.CONFLICT;
            }
        }
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status, error, message, request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        log.error("Validation failed: {}", ex.getMessage());

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid input data")
                .path(request.getDescription(false).replace("uri=", ""))
                .validationErrors(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, WebRequest request) {
        log.error("File size exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ErrorResponse.of(HttpStatus.CONTENT_TOO_LARGE, "File Too Large",
                        "CSV file size exceeds maximum allowed size (10MB)", request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        log.error("Illegal argument error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, BAD_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        log.warn("Invalid query param '{}' = '{}'", ex.getName(), ex.getValue());
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, BAD_REQUEST, message, request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, WebRequest request) {
        log.warn("Method not supported: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED,
                "The " + ex.getMethod() + " method is not supported for this endpoint.", request);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            builder.allow(supported.toArray(new HttpMethod[0]));
        }
        return builder.body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                        "An unexpected error occurred", request));
    }
}
