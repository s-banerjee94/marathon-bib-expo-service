package com.timekeeper.bibexpo.demo.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.timekeeper.bibexpo.demo.config.DemoProperties;
import com.timekeeper.bibexpo.demo.exception.DemoRateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window per-IP rate limiter for the public demo endpoints. Counters live in a
 * self-contained Caffeine cache whose one-minute write-expiry is the window; abuse
 * protection for a toy surface, not precise traffic shaping.
 */
@Component
@RequiredArgsConstructor
public class DemoRateLimiter {

    private final DemoProperties properties;

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    public void checkCreate(String clientIp) {
        check("create:" + clientIp, properties.getCreateLimitPerMinute());
    }

    public void checkCollect(String clientIp) {
        check("collect:" + clientIp, properties.getCollectLimitPerMinute());
    }

    private void check(String key, int limit) {
        if (counters.get(key, k -> new AtomicInteger()).incrementAndGet() > limit) {
            throw new DemoRateLimitExceededException();
        }
    }
}
