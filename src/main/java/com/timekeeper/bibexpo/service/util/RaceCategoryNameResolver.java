package com.timekeeper.bibexpo.service.util;

import com.timekeeper.bibexpo.config.CacheConfig;
import com.timekeeper.bibexpo.repository.CategoryRepository;
import com.timekeeper.bibexpo.repository.RaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the current race/category names for an event so participant records, which store only
 * {@code raceId}/{@code categoryId}, can be enriched with up-to-date names at read time. The lookup
 * maps are cached by event id, since the same event's races and categories rarely change but are read
 * on every participant scan; entries are evicted on any race/category write for the event.
 */
@Component
@RequiredArgsConstructor
public class RaceCategoryNameResolver {

    private final RaceRepository raceRepository;
    private final CategoryRepository categoryRepository;
    private final CacheManager cacheManager;

    @Cacheable(value = CacheConfig.EVENT_NAMES_CACHE, key = "#eventId")
    public EventNames forEvent(Long eventId) {
        Map<String, String> raceNames = new HashMap<>();
        Map<String, Instant> raceReportingTimes = new HashMap<>();
        raceRepository.findByEventIdAndDeletedFalse(eventId).forEach(race -> {
            String key = String.valueOf(race.getId());
            raceNames.put(key, race.getRaceName());
            if (race.getReportingTime() != null) {
                raceReportingTimes.put(key, race.getReportingTime());
            }
        });

        Map<String, String> categoryNames = new HashMap<>();
        Map<String, String> categoryRaceIds = new HashMap<>();
        categoryRepository.findByRaceEventId(eventId).forEach(category -> {
            String key = String.valueOf(category.getId());
            categoryNames.put(key, category.getCategoryName());
            if (category.getRace() != null) {
                categoryRaceIds.put(key, String.valueOf(category.getRace().getId()));
            }
        });

        return new EventNames(raceNames, categoryNames, raceReportingTimes, categoryRaceIds);
    }

    /**
     * Drops the cached names for the given event. When called inside a transaction the eviction runs
     * only after commit, so a concurrent read cannot re-cache stale names between the write and its
     * commit.
     *
     * @param eventId the event whose cached names should be dropped (no-op if null)
     */
    public void evict(Long eventId) {
        if (eventId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(eventId);
                }
            });
        } else {
            evictNow(eventId);
        }
    }

    private void evictNow(Long eventId) {
        Cache cache = cacheManager.getCache(CacheConfig.EVENT_NAMES_CACHE);
        if (cache != null) {
            cache.evict(eventId);
        }
    }

    public record EventNames(Map<String, String> raceNames, Map<String, String> categoryNames,
                             Map<String, Instant> raceReportingTimes, Map<String, String> categoryRaceIds) {

        public String raceName(String raceId) {
            return raceId == null ? null : raceNames.get(raceId);
        }

        public String categoryName(String categoryId) {
            return categoryId == null ? null : categoryNames.get(categoryId);
        }

        public Instant reportingTime(String raceId) {
            return raceId == null ? null : raceReportingTimes.get(raceId);
        }

        public String categoryRaceId(String categoryId) {
            return categoryId == null ? null : categoryRaceIds.get(categoryId);
        }
    }
}
