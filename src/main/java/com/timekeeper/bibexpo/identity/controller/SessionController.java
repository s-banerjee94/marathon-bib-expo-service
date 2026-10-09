package com.timekeeper.bibexpo.identity.controller;

import com.timekeeper.bibexpo.identity.exception.SessionNotFoundException;
import com.timekeeper.bibexpo.identity.model.dto.response.SessionResponse;
import com.timekeeper.bibexpo.identity.service.JwtService;
import com.timekeeper.bibexpo.identity.service.SessionService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SessionController implements SessionControllerApi {

    private final SessionService sessionService;
    private final JwtService jwtService;

    @Override
    public ResponseEntity<List<SessionResponse>> listMySessions(User currentUser, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                sessionService.listSessions(currentUser.getUsername(), currentSid(httpRequest)));
    }

    @Override
    public ResponseEntity<Void> endMySession(String sessionId, User currentUser) {
        if (!sessionService.endSession(currentUser.getUsername(), sessionId)) {
            throw new SessionNotFoundException("That device is not signed in.");
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> endMyOtherSessions(User currentUser, HttpServletRequest httpRequest) {
        sessionService.endOtherSessions(currentUser.getUsername(), currentSid(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /** The sid of the access token on this request, which the auth filter has already validated. */
    private String currentSid(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return jwtService.extractSid(header.substring(7));
    }

    // Whether the sid is unknown or simply belongs to someone else is deliberately not
    // distinguished, so a caller cannot probe for other accounts' session ids.
    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException ex, WebRequest request) {
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
