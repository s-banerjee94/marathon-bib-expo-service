package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.model.entity.Category;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.Race;
import com.timekeeper.bibexpo.repository.CategoryRepository;
import com.timekeeper.bibexpo.repository.RaceRepository;
import com.timekeeper.bibexpo.util.NameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchReferenceDataService {

    private final RaceRepository raceRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Race findOrCreateRace(String raceName, Long eventId, Event event) {
        String normalizedName = NameNormalizer.toStoredName(raceName);
        return raceRepository.findByRaceNameAndEventIdAndDeletedFalse(normalizedName, eventId)
                .orElseGet(() -> {
                    log.info("Creating race '{}' for event {}", normalizedName, eventId);
                    return raceRepository.save(Race.builder()
                            .raceName(normalizedName)
                            .raceDescription("Auto-created from CSV import")
                            .event(event)
                            .deleted(false)
                            .build());
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Category findOrCreateCategory(String categoryName, Race race) {
        String normalizedName = NameNormalizer.toStoredName(categoryName);
        return categoryRepository.findByCategoryNameAndRaceId(normalizedName, race.getId())
                .orElseGet(() -> {
                    log.info("Creating category '{}' for race {}", normalizedName, race.getId());
                    return categoryRepository.save(Category.builder()
                            .categoryName(normalizedName)
                            .description("Auto-created from CSV import")
                            .race(race)
                            .build());
                });
    }
}
