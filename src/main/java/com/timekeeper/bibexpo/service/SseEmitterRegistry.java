package com.timekeeper.bibexpo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory registry mapping userId → list of SseEmitters (one per open browser tab).
 * All tabs belonging to the same user receive every push event.
 * For multi-instance deployments, replace with Redis Pub/Sub.
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    private static final long SSE_TIMEOUT_MS = 15 * 60 * 1000L; // 15 minutes

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    /**
     * Registers a new SSE emitter for the given user (one per browser tab).
     * When the tab closes or times out, that specific emitter is removed automatically.
     */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitters.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        // onCompletion fires for all cases: normal close, timeout, and error
        emitter.onCompletion(() -> removeSingle(userId, emitter));
        emitter.onTimeout(emitter::complete);  // triggers onCompletion
        emitter.onError(e -> {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // Connection already dead — remove directly without completing
                removeSingle(userId, emitter);
            }
        });

        log.debug("SSE tab subscribed for user {} — active tabs: {}",
                userId, emitters.getOrDefault(userId, new CopyOnWriteArrayList<>()).size());

        try {
            emitter.send(SseEmitter.event().name("connection:open").data("connected"));
        } catch (IOException e) {
            removeSingle(userId, emitter);
        }

        return emitter;
    }

    /**
     * Pushes data to ALL open tabs of the given user.
     *
     * @param eventName SSE event name (e.g. {@code import:completed}, {@code import:failed})
     */
    public void send(Long userId, String eventName, Object data) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            log.debug("No active SSE tabs for user {}, skipping push", userId);
            return;
        }

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                // Client disconnected or connection already closed — remove stale emitter
                log.debug("SSE send failed for one tab of user {} (client disconnected), removing it", userId);
                removeSingle(userId, emitter);
            }
        }

        log.debug("SSE notification pushed to {} tab(s) for user {}", userEmitters.size(), userId);
    }

    /**
     * Removes all open SSE connections for the user — call this on logout.
     */
    public void removeAll(Long userId) {
        List<SseEmitter> userEmitters = emitters.remove(userId);
        if (userEmitters != null) {
            userEmitters.forEach(SseEmitter::complete);
            log.debug("All SSE tabs closed for user {} on logout", userId);
        }
    }

    private void removeSingle(Long userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        }
        log.debug("SSE tab removed for user {}", userId);
    }
}
