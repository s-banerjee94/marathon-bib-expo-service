package com.timekeeper.bibexpo.messaging.provider.service;

import com.timekeeper.bibexpo.config.CacheConfig;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.repository.MessagingProviderRepository;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/**
 * Caches the provider rows resolved on every outbound send: an organization's CAMPAIGN override, the
 * platform-default CAMPAIGN row, and the SYSTEM transactional row, each keyed by channel. Empty
 * lookups are cached as well, so an organization with no override does not re-query on every send.
 * Eviction is deferred to after the surrounding transaction commits, matching the admin save/delete
 * paths.
 */
@Component
@RequiredArgsConstructor
public class MessagingProviderCache {

    private final MessagingProviderRepository providerRepository;
    private final CacheManager cacheManager;

    @Cacheable(value = CacheConfig.MESSAGING_PROVIDERS_CACHE, key = "'CO:' + #channel + ':' + #organizationId")
    public Optional<MessagingProvider> findCampaignOverride(MessageChannel channel, Long organizationId) {
        return providerRepository.findByUsageAndChannelAndOrganizationId(MessageUsage.CAMPAIGN, channel, organizationId);
    }

    @Cacheable(value = CacheConfig.MESSAGING_PROVIDERS_CACHE, key = "'CD:' + #channel")
    public Optional<MessagingProvider> findCampaignDefault(MessageChannel channel) {
        return providerRepository.findByUsageAndChannelAndOrganizationIdIsNull(MessageUsage.CAMPAIGN, channel);
    }

    @Cacheable(value = CacheConfig.MESSAGING_PROVIDERS_CACHE, key = "'SYS:' + #channel")
    public Optional<MessagingProvider> findSystem(MessageChannel channel) {
        return providerRepository.findByUsageAndChannelAndOrganizationIdIsNull(MessageUsage.SYSTEM, channel);
    }

    public void evictCampaignOverride(MessageChannel channel, Long organizationId) {
        evict("CO:" + channel + ":" + organizationId);
    }

    public void evictCampaignDefault(MessageChannel channel) {
        evict("CD:" + channel);
    }

    public void evictSystem(MessageChannel channel) {
        evict("SYS:" + channel);
    }

    private void evict(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(key);
                }
            });
        } else {
            evictNow(key);
        }
    }

    private void evictNow(String key) {
        Cache cache = cacheManager.getCache(CacheConfig.MESSAGING_PROVIDERS_CACHE);
        if (cache != null) {
            cache.evict(key);
        }
    }
}
