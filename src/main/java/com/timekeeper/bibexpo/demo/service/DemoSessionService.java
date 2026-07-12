package com.timekeeper.bibexpo.demo.service;

import com.timekeeper.bibexpo.demo.model.dto.response.DemoSessionResponse;
import com.timekeeper.bibexpo.demo.model.dto.response.DemoSessionStatusResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Landing-page live QR demo sessions: fabricated runners in an in-memory TTL store,
 * with zero contact with real participant, event, or distribution data.
 */
public interface DemoSessionService {

    /**
     * Creates a session with a fabricated runner for the desktop bib card.
     *
     * @param clientIp caller IP, rate limited per minute
     * @return the session code (encoded into the QR), runner details, and expiry
     */
    DemoSessionResponse createSession(String clientIp);

    /**
     * Returns the session for the phone's distributor view and marks it SCANNED on first read.
     *
     * @param code the session code from the scanned QR
     * @return the same shape as creation: code, runner details, and expiry
     */
    DemoSessionResponse getSession(String code);

    /**
     * Marks the session collected; single-use — the first tap wins, later taps get a conflict.
     *
     * @param code     the session code from the scanned QR
     * @param clientIp caller IP, rate limited per minute
     * @return the terminal COLLECTED status
     */
    DemoSessionStatusResponse collectSession(String code, String clientIp);

    /**
     * Returns only the current status, polled by the desktop while the QR is on screen.
     *
     * @param code the session code
     * @return CREATED, SCANNED, or COLLECTED
     */
    DemoSessionStatusResponse getSessionStatus(String code);

    /**
     * Opens an SSE stream that pushes status changes to the desktop instantly; the current
     * status is sent on connect and the stream closes itself on COLLECTED or session expiry.
     * Polling {@link #getSessionStatus} remains the client's fallback.
     *
     * @param code the session code
     * @return the emitter for the open stream
     */
    SseEmitter streamSessionEvents(String code);
}
