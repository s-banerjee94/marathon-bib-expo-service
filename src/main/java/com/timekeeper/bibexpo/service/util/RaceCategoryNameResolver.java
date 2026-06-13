package com.timekeeper.bibexpo.service.util;

import com.timekeeper.bibexpo.repository.CategoryRepository;
import com.timekeeper.bibexpo.repository.RaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the current race/category names for an event so participant records, which store only
 * {@code raceId}/{@code categoryId}, can be enriched with up-to-date names at read time. Loading the
 * event's races and categories once yields a small, bounded lookup map reused across a request.
 */
@Component
@RequiredArgsConstructor
public class RaceCategoryNameResolver {

    private final RaceRepository raceRepository;
    private final CategoryRepository categoryRepository;

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
        categoryRepository.findByRaceEventId(eventId)
                .forEach(category -> categoryNames.put(String.valueOf(category.getId()), category.getCategoryName()));

        return new EventNames(raceNames, categoryNames, raceReportingTimes);
    }

    public record EventNames(Map<String, String> raceNames, Map<String, String> categoryNames,
                             Map<String, Instant> raceReportingTimes) {

        public String raceName(String raceId) {
            return raceId == null ? null : raceNames.get(raceId);
        }

        public String categoryName(String categoryId) {
            return categoryId == null ? null : categoryNames.get(categoryId);
        }

        public Instant reportingTime(String raceId) {
            return raceId == null ? null : raceReportingTimes.get(raceId);
        }
    }
}
