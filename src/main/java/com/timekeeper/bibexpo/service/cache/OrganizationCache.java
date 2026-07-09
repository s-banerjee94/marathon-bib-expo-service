package com.timekeeper.bibexpo.service.cache;

import com.timekeeper.bibexpo.config.CacheConfig;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Caches organizations by id, since organization-scoped users read their own organization
 * repeatedly. Only the entity is cached, not the response, so each read still presigns a fresh
 * logo URL. Eviction is deferred to after the surrounding transaction commits.
 */
@Component
@RequiredArgsConstructor
public class OrganizationCache {

    private final OrganizationRepository organizationRepository;
    private final CacheManager cacheManager;

    /**
     * Returns the organization for the given id, or null if it does not exist.
     * Null results are not cached, so a later create is picked up immediately.
     *
     * @param id the organization id
     * @return the organization, or null
     */
    @Cacheable(value = CacheConfig.ORGANIZATIONS_CACHE, key = "#id", unless = "#result == null")
    public Organization findActiveById(Long id) {
        return organizationRepository.findById(id).orElse(null);
    }

    /**
     * Drops the cached organization for the given id. When called inside a transaction the eviction
     * runs only after commit; otherwise it runs immediately.
     *
     * @param id the organization id whose cached entry should be dropped (no-op if null)
     */
    public void evict(Long id) {
        if (id == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(id);
                }
            });
        } else {
            evictNow(id);
        }
    }

    private void evictNow(Long id) {
        Cache cache = cacheManager.getCache(CacheConfig.ORGANIZATIONS_CACHE);
        if (cache != null) {
            cache.evict(id);
        }
    }
}
