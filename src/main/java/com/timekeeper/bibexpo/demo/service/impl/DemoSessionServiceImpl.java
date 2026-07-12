package com.timekeeper.bibexpo.demo.service.impl;

import com.timekeeper.bibexpo.demo.config.DemoProperties;
import com.timekeeper.bibexpo.demo.exception.DemoCapacityExceededException;
import com.timekeeper.bibexpo.demo.exception.DemoSessionAlreadyCollectedException;
import com.timekeeper.bibexpo.demo.exception.DemoSessionExpiredException;
import com.timekeeper.bibexpo.demo.exception.DemoSessionNotFoundException;
import com.timekeeper.bibexpo.demo.model.DemoSession;
import com.timekeeper.bibexpo.demo.model.DemoSessionStatus;
import com.timekeeper.bibexpo.demo.model.dto.response.DemoRunnerResponse;
import com.timekeeper.bibexpo.demo.model.dto.response.DemoSessionResponse;
import com.timekeeper.bibexpo.demo.model.dto.response.DemoSessionStatusResponse;
import com.timekeeper.bibexpo.demo.service.DemoRateLimiter;
import com.timekeeper.bibexpo.demo.service.DemoSessionEvents;
import com.timekeeper.bibexpo.demo.service.DemoSessionService;
import com.timekeeper.bibexpo.demo.store.DemoSessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemoSessionServiceImpl implements DemoSessionService {

    private record RunnerSeed(String name, String gender) {
    }

    private static final List<RunnerSeed> RUNNER_POOL = List.of(
            new RunnerSeed("Asha Verma", "F"),
            new RunnerSeed("Rahul Nair", "M"),
            new RunnerSeed("Priya Sharma", "F"),
            new RunnerSeed("Arjun Mehta", "M"),
            new RunnerSeed("Kavya Iyer", "F"),
            new RunnerSeed("Vikram Singh", "M"),
            new RunnerSeed("Neha Kulkarni", "F"),
            new RunnerSeed("Rohan Das", "M"),
            new RunnerSeed("Ananya Reddy", "F"),
            new RunnerSeed("Karan Patel", "M"),
            new RunnerSeed("Meera Joshi", "F"),
            new RunnerSeed("Aditya Rao", "M"),
            new RunnerSeed("Divya Menon", "F"),
            new RunnerSeed("Farhan Khan", "M"),
            new RunnerSeed("Sneha Pillai", "F"),
            new RunnerSeed("Sameer Gupta", "M"));

    private static final List<String> AGE_BANDS =
            List.of("18–24", "25–29", "30–34", "35–39", "40–44", "45–49");

    private final DemoSessionStore store;
    private final DemoRateLimiter rateLimiter;
    private final DemoProperties properties;
    private final DemoSessionEvents events;

    @Override
    public DemoSessionResponse createSession(String clientIp) {
        rateLimiter.checkCreate(clientIp);
        if (store.liveCount() >= properties.getMaxLiveSessions()) {
            throw new DemoCapacityExceededException();
        }
        DemoSession session = fabricateSession();
        String code = store.issue(session);
        return toResponse(code, session);
    }

    @Override
    public DemoSessionResponse getSession(String code) {
        DemoSession session = requireSession(code);
        if (session.isExpired()) {
            throw new DemoSessionExpiredException();
        }
        if (session.markScanned()) {
            events.publish(code, DemoSessionStatus.SCANNED);
        }
        return toResponse(code, session);
    }

    @Override
    public DemoSessionStatusResponse collectSession(String code, String clientIp) {
        rateLimiter.checkCollect(clientIp);
        DemoSession session = requireSession(code);
        if (session.getStatus() == DemoSessionStatus.COLLECTED) {
            throw new DemoSessionAlreadyCollectedException();
        }
        if (session.isExpired()) {
            throw new DemoSessionExpiredException();
        }
        if (!session.markCollected()) {
            throw new DemoSessionAlreadyCollectedException();
        }
        events.publish(code, DemoSessionStatus.COLLECTED);
        log.info("Demo bib {} collected", session.getBib());
        return new DemoSessionStatusResponse(DemoSessionStatus.COLLECTED);
    }

    @Override
    public DemoSessionStatusResponse getSessionStatus(String code) {
        DemoSession session = requireSession(code);
        if (session.isExpired()) {
            throw new DemoSessionExpiredException();
        }
        return new DemoSessionStatusResponse(session.getStatus());
    }

    @Override
    public SseEmitter streamSessionEvents(String code) {
        DemoSession session = requireSession(code);
        if (session.isExpired()) {
            throw new DemoSessionExpiredException();
        }
        return events.subscribe(code, session);
    }

    private DemoSession fabricateSession() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        RunnerSeed runner = RUNNER_POOL.get(random.nextInt(RUNNER_POOL.size()));
        return DemoSession.builder()
                .runnerName(runner.name())
                .bib(String.valueOf(random.nextInt(10_000, 100_000)))
                .category(runner.gender() + " " + AGE_BANDS.get(random.nextInt(AGE_BANDS.size())))
                .expiresAt(Instant.now().plus(properties.getSessionTtlMinutes(), ChronoUnit.MINUTES))
                .build();
    }

    private DemoSession requireSession(String code) {
        DemoSession session = store.peek(code);
        if (session == null) {
            throw new DemoSessionNotFoundException();
        }
        return session;
    }

    private DemoSessionResponse toResponse(String code, DemoSession session) {
        return DemoSessionResponse.builder()
                .code(code)
                .runner(DemoRunnerResponse.builder()
                        .name(session.getRunnerName())
                        .bib(session.getBib())
                        .category(session.getCategory())
                        .build())
                .expiresAt(session.getExpiresAt())
                .build();
    }
}
