package com.timekeeper.bibexpo.demo.controller;

import com.timekeeper.bibexpo.demo.exception.DemoCapacityExceededException;
import com.timekeeper.bibexpo.demo.exception.DemoRateLimitExceededException;
import com.timekeeper.bibexpo.demo.exception.DemoSessionAlreadyCollectedException;
import com.timekeeper.bibexpo.demo.exception.DemoSessionExpiredException;
import com.timekeeper.bibexpo.demo.exception.DemoSessionNotFoundException;
import com.timekeeper.bibexpo.demo.model.dto.response.DemoSessionResponse;
import com.timekeeper.bibexpo.demo.model.dto.response.DemoSessionStatusResponse;
import com.timekeeper.bibexpo.demo.service.DemoSessionService;
import com.timekeeper.bibexpo.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DemoSessionController implements DemoSessionControllerApi {

    private final DemoSessionService demoSessionService;

    @Override
    public ResponseEntity<DemoSessionResponse> createSession(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(demoSessionService.createSession(clientIp(request)));
    }

    @Override
    public ResponseEntity<DemoSessionResponse> getSession(@PathVariable String code) {
        return ResponseEntity.ok(demoSessionService.getSession(code));
    }

    @Override
    public ResponseEntity<DemoSessionStatusResponse> collectSession(@PathVariable String code,
                                                                    HttpServletRequest request) {
        return ResponseEntity.ok(demoSessionService.collectSession(code, clientIp(request)));
    }

    @Override
    public ResponseEntity<DemoSessionStatusResponse> getSessionStatus(@PathVariable String code) {
        return ResponseEntity.ok(demoSessionService.getSessionStatus(code));
    }

    @Override
    public ResponseEntity<SseEmitter> streamSessionEvents(@PathVariable String code) {
        // X-Accel-Buffering stops nginx from buffering the stream, which would delay events
        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .body(demoSessionService.streamSessionEvents(code));
    }

    /** First X-Forwarded-For hop when behind the reverse proxy, otherwise the socket address. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @ExceptionHandler(DemoSessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(DemoSessionNotFoundException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }

    @ExceptionHandler(DemoSessionExpiredException.class)
    public ResponseEntity<ErrorResponse> handleExpired(DemoSessionExpiredException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ErrorResponse.of(HttpStatus.GONE, "Gone", ex.getMessage(), request));
    }

    @ExceptionHandler(DemoSessionAlreadyCollectedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyCollected(DemoSessionAlreadyCollectedException ex,
                                                                WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    @ExceptionHandler({DemoRateLimitExceededException.class, DemoCapacityExceededException.class})
    public ResponseEntity<ErrorResponse> handleThrottled(RuntimeException ex, WebRequest request) {
        log.warn("Demo request throttled: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage(), request));
    }
}
