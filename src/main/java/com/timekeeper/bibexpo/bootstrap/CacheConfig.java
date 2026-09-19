package com.timekeeper.bibexpo.bootstrap;

import com.github.benmanes.caffeine.cache.Cache;
import com.timekeeper.bibexpo.shared.cache.CacheNames;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.timekeeper.bibexpo.demo.config.DemoProperties;
import com.timekeeper.bibexpo.demo.model.DemoSession;
import com.timekeeper.bibexpo.invitation.config.InviteProperties;
import com.timekeeper.bibexpo.invitation.model.Invitation;
import com.timekeeper.bibexpo.passwordreset.config.PasswordResetProperties;
import com.timekeeper.bibexpo.passwordreset.model.PasswordResetToken;
import com.timekeeper.bibexpo.reporting.service.OrgDashboardService;
import com.timekeeper.bibexpo.reporting.service.PlatformDashboardService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

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
        manager.registerCustomCache(PlatformDashboardService.DASHBOARD_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(200)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .build());
        manager.registerCustomCache(CacheNames.UNREAD_COUNTS_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .build());
        manager.registerCustomCache(CacheNames.USER_DETAILS_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .build());
        manager.registerCustomCache(CacheNames.ORGANIZATIONS_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(50)
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .build());
        manager.registerCustomCache(CacheNames.MESSAGING_PROVIDERS_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(200)
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .build());
        manager.registerCustomCache(CacheNames.SYSTEM_TEMPLATES_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .build());
        manager.registerCustomCache(CacheNames.EVENT_NAMES_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(1, TimeUnit.HOURS)
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

    /**
     * Dedicated native cache for pending password resets. A native Caffeine cache (not the
     * CacheManager) is used so the store can atomically remove-and-return on completion.
     * The write-expiry is the reset link lifetime.
     */
    @Bean
    public Cache<String, PasswordResetToken> passwordResetCache(PasswordResetProperties passwordResetProperties) {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(passwordResetProperties.getTtlMinutes(), TimeUnit.MINUTES)
                .build();
    }

    /**
     * Dedicated native cache for landing-page demo sessions. The write-expiry is the session
     * retention window (longer than the session TTL): entries outlive their logical expiry so a
     * phone scanning a stale QR gets a clean "expired" (410) answer instead of "not found" until
     * the entry is evicted.
     */
    @Bean
    public Cache<String, DemoSession> demoSessionCache(DemoProperties demoProperties) {
        // Never evict before the session TTL, or a still-valid QR would start answering 404.
        long retentionMinutes = Math.max(
                demoProperties.getSessionRetentionMinutes(), demoProperties.getSessionTtlMinutes());
        return Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(retentionMinutes, TimeUnit.MINUTES)
                .build();
    }
}
