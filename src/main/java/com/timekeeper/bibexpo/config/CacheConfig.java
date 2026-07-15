package com.timekeeper.bibexpo.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.timekeeper.bibexpo.demo.config.DemoProperties;
import com.timekeeper.bibexpo.demo.model.DemoSession;
import com.timekeeper.bibexpo.invitation.config.InviteProperties;
import com.timekeeper.bibexpo.invitation.model.Invitation;
import com.timekeeper.bibexpo.passwordreset.config.PasswordResetProperties;
import com.timekeeper.bibexpo.passwordreset.model.PasswordResetToken;
import com.timekeeper.bibexpo.service.dashboard.OrgDashboardService;
import com.timekeeper.bibexpo.service.dashboard.PlatformDashboardService;
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

    /**
     * Authenticated user entities keyed by username, loaded on every request by the JWT filter.
     * Each entry carries its organization and event eagerly so the cached (detached) user stays
     * usable outside a session. Evicted on any user mutation and on organization disable; the
     * one-hour write-expiry is only a backstop.
     */
    public static final String USER_DETAILS_CACHE = "userDetails";

    /**
     * Active organizations keyed by id, read repeatedly by organization-scoped users. Evicted on
     * every organization write (update, status toggle, logo change); the one-hour write-expiry is
     * only a backstop.
     */
    public static final String ORGANIZATIONS_CACHE = "organizations";

    /**
     * Provider configuration rows resolved on every outbound send: the SYSTEM transactional row and
     * the CAMPAIGN default/override rows, keyed by channel (and organization). Root-managed and
     * rarely changed, so entries are evicted on the admin save/delete paths; the one-hour
     * write-expiry is only a backstop. Empty lookups (an organization with no override) are cached
     * too, so the common fall-through to the default costs no query.
     */
    public static final String MESSAGING_PROVIDERS_CACHE = "messagingProviders";

    /**
     * System message templates keyed by purpose and channel, resolved on every transactional send
     * (notification, invitation, participant-event SMS/WhatsApp). Root-managed and rarely changed, so
     * entries are evicted on the admin save path; the one-hour write-expiry is only a backstop.
     */
    public static final String SYSTEM_TEMPLATES_CACHE = "systemMessageTemplates";

    /**
     * Per-event race and category name lookup maps, keyed by event id, used to enrich reads that only
     * store race/category ids. Evicted whenever the event's races or categories change
     * (create/update/delete and CSV import); the one-hour write-expiry is only a backstop.
     */
    public static final String EVENT_NAMES_CACHE = "eventRaceCategoryNames";

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
        manager.registerCustomCache(UNREAD_COUNTS_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .build());
        manager.registerCustomCache(USER_DETAILS_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .build());
        manager.registerCustomCache(ORGANIZATIONS_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(50)
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .build());
        manager.registerCustomCache(MESSAGING_PROVIDERS_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(200)
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .build());
        manager.registerCustomCache(SYSTEM_TEMPLATES_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .build());
        manager.registerCustomCache(EVENT_NAMES_CACHE,
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
