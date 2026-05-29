package com.timekeeper.bibexpo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.timekeeper.bibexpo.service.dashboard.OrgDashboardService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ACTIVE_SESSIONS_CACHE = "activeSessions";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(60, TimeUnit.SECONDS));
        manager.registerCustomCache(OrgDashboardService.DASHBOARD_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .build());
        return manager;
    }
}
