package com.timekeeper.bibexpo.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.timekeeper.bibexpo.invitation.config.InviteProperties;
import com.timekeeper.bibexpo.invitation.model.Invitation;
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

    /**
     * Unread-notification badge counts, keyed by user id. Kept correct by explicit eviction on every
     * write path (new notification, mark-read, delete); the write-expiry is only a backstop for a
     * missed eviction. Lets the frequent badge poll skip DynamoDB between changes.
     */
    public static final String UNREAD_COUNTS_CACHE = "unreadNotificationCounts";

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
        manager.registerCustomCache(UNREAD_COUNTS_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .build());
        return manager;
    }

    /**
     * Dedicated native cache for pending invites. A native Caffeine cache (not the
     * CacheManager) is used so the store can atomically remove-and-return on accept.
     * The write-expiry is the invite link lifetime.
     */
    @Bean
    public Cache<String, Invitation> invitationCache(InviteProperties inviteProperties) {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(inviteProperties.getTtlMinutes(), TimeUnit.MINUTES)
                .build();
    }
}
