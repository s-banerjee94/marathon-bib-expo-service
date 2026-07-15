package com.timekeeper.bibexpo.demo.service;

import com.timekeeper.bibexpo.demo.model.DemoSession;
import com.timekeeper.bibexpo.demo.model.DemoSessionStatus;
import com.timekeeper.bibexpo.demo.model.dto.response.DemoSessionStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open SSE connections from landing-page desktops, one per session code. Connections are
 * bounded by construction: the emitter timeout is the session's remaining TTL, a new
 * subscription for the same code replaces the old one, and the global live-session cap
 * also caps concurrent streams. Polling /status remains the client's fallback.
 */
@Component
@Slf4j
public class DemoSessionEvents {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Opens a stream for the session and immediately sends the current status, so a
     * desktop reconnecting after a missed event is reconciled on the spot.
     */
    public SseEmitter subscribe(String code, DemoSession session) {
        long remainingMillis = Math.max(Duration.between(Instant.now(), session.getExpiresAt()).toMillis(), 1);
        SseEmitter emitter = new SseEmitter(remainingMillis);
        emitter.onCompletion(() -> emitters.remove(code, emitter));
        // Complete the emitter on timeout so the async request ends cleanly. Without this, Spring
        // raises AsyncRequestTimeoutException, which the global handler then tries to render as a
        // JSON ErrorResponse into a text/event-stream response (unwritable).
        emitter.onTimeout(() -> {
            emitters.remove(code, emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(code, emitter));
        SseEmitter previous = emitters.put(code, emitter);
        if (previous != null) {
            previous.complete();
        }
        DemoSessionStatus current = session.getStatus();
        send(code, emitter, current);
        if (current == DemoSessionStatus.COLLECTED) {
            emitter.complete();
        }
        return emitter;
    }

    /**
     * Pushes a status change to the session's desktop, if one is listening; COLLECTED is
     * terminal, so the stream is closed after it.
     */
    public void publish(String code, DemoSessionStatus status) {
        SseEmitter emitter = emitters.get(code);
        if (emitter == null) {
            return;
        }
        send(code, emitter, status);
        if (status == DemoSessionStatus.COLLECTED) {
            emitter.complete();
        }
    }

    /** Keeps idle streams alive through reverse proxies that cut quiet connections. */
    @Scheduled(fixedDelay = 20_000)
    public void heartbeat() {
        emitters.forEach((code, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (Exception e) {
                emitters.remove(code, emitter);
            }
        });
    }

    private void send(String code, SseEmitter emitter, DemoSessionStatus status) {
        try {
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(new DemoSessionStatusResponse(status), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            emitters.remove(code, emitter);
        }
    }
}
