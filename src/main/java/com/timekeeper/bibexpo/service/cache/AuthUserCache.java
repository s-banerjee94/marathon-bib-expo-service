package com.timekeeper.bibexpo.service.cache;

import com.timekeeper.bibexpo.config.CacheConfig;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Caches the authenticated user (with organization and event eagerly loaded) by username, so the
 * JWT filter's per-request lookup does not hit the database on every call. Eviction is deferred to
 * after the surrounding transaction commits, so a concurrent authentication cannot re-cache a stale
 * user between the write and its commit.
 */
@Component
@RequiredArgsConstructor
public class AuthUserCache {

    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    @Cacheable(value = CacheConfig.USER_DETAILS_CACHE, key = "#username", unless = "#result == null")
    public User findByUsername(String username) {
        return userRepository.findByUsernameWithOrganizationAndEvent(username).orElse(null);
    }

    /**
     * Drops the cached user for the given username. When called inside a transaction the eviction
     * runs only after commit; otherwise it runs immediately.
     *
     * @param username the username whose cached entry should be dropped (no-op if null)
     */
    public void evict(String username) {
        if (username == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(username);
                }
            });
        } else {
            evictNow(username);
        }
    }

    private void evictNow(String username) {
        Cache cache = cacheManager.getCache(CacheConfig.USER_DETAILS_CACHE);
        if (cache != null) {
            cache.evict(username);
        }
    }
}
