package com.timekeeper.bibexpo.demo.model;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A short-lived demo session with a fabricated runner, shared between the landing-page
 * desktop (polling) and a visitor's phone (collecting). State transitions are lock-free
 * CAS so concurrent scans and collects resolve to a single winner.
 */
@Getter
@Builder
public class DemoSession {

    private final String runnerName;
    private final String bib;
    private final String category;
    private final Instant expiresAt;

    @Getter(AccessLevel.NONE)
    @Builder.Default
    private final AtomicReference<DemoSessionStatus> status =
            new AtomicReference<>(DemoSessionStatus.CREATED);

    public DemoSessionStatus getStatus() {
        return status.get();
    }

    /** Records the phone's first read; returns true only on the CREATED-to-SCANNED transition. */
    public boolean markScanned() {
        return status.compareAndSet(DemoSessionStatus.CREATED, DemoSessionStatus.SCANNED);
    }

    /** Single-use collect: returns true only for the first winning tap. */
    public boolean markCollected() {
        return status.compareAndSet(DemoSessionStatus.CREATED, DemoSessionStatus.COLLECTED)
                || status.compareAndSet(DemoSessionStatus.SCANNED, DemoSessionStatus.COLLECTED);
    }

    /** A session collected before its TTL stays COLLECTED; expiry only applies to live ones. */
    public boolean isExpired() {
        return getStatus() != DemoSessionStatus.COLLECTED && Instant.now().isAfter(expiresAt);
    }
}
