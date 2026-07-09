package com.timekeeper.bibexpo.messaging.system.service;

import com.timekeeper.bibexpo.config.CacheConfig;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.messaging.system.model.entity.SystemMessageTemplate;
import com.timekeeper.bibexpo.messaging.system.repository.SystemMessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/**
 * Caches the system message template for a purpose and channel, resolved on every transactional
 * send. Empty lookups are cached too, so a missing configuration does not re-query on every call.
 * Eviction is deferred to after the surrounding transaction commits, matching the admin save path.
 */
@Component
@RequiredArgsConstructor
public class SystemMessageTemplateCache {

    private final SystemMessageTemplateRepository templateRepository;
    private final CacheManager cacheManager;

    @Cacheable(value = CacheConfig.SYSTEM_TEMPLATES_CACHE, key = "#purpose + ':' + #channel")
    public Optional<SystemMessageTemplate> findByPurposeAndChannel(SystemTemplatePurpose purpose, MessageChannel channel) {
        return templateRepository.findByPurposeAndChannel(purpose, channel);
    }

    public void evict(SystemTemplatePurpose purpose, MessageChannel channel) {
        String key = purpose + ":" + channel;
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
        Cache cache = cacheManager.getCache(CacheConfig.SYSTEM_TEMPLATES_CACHE);
        if (cache != null) {
            cache.evict(key);
        }
    }
}
