package com.timekeeper.bibexpo.event.race.service.impl;

import com.timekeeper.bibexpo.event.api.RaceCategoryStore;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.race.category.model.entity.Category;
import com.timekeeper.bibexpo.event.race.category.repository.CategoryRepository;
import com.timekeeper.bibexpo.event.race.model.entity.Race;
import com.timekeeper.bibexpo.event.race.repository.RaceRepository;
import com.timekeeper.bibexpo.event.race.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.shared.util.NameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RaceCategoryStoreImpl implements RaceCategoryStore {

    private final RaceRepository raceRepository;
    private final CategoryRepository categoryRepository;
    private final RaceCategoryNameResolver nameResolver;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Race findOrCreateRace(String rawName, Long eventId, Event event) {
        String normalizedName = NameNormalizer.toStoredName(rawName);
        return raceRepository.findByRaceNameAndEventIdAndDeletedFalse(normalizedName, eventId)
                .orElseGet(() -> {
                    log.info("Creating race '{}' for event {}", normalizedName, eventId);
                    Race created = raceRepository.save(Race.builder()
                            .raceName(normalizedName)
                            .raceDescription("Auto-created from CSV import")
                            .event(event)
                            .deleted(false)
                            .build());
                    nameResolver.evict(eventId);
                    return created;
                });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Category findOrCreateCategory(String rawName, Long eventId, Race race) {
        String normalizedName = NameNormalizer.toStoredName(rawName);
        return categoryRepository.findByCategoryNameAndRaceId(normalizedName, race.getId())
                .orElseGet(() -> {
                    log.info("Creating category '{}' for race {}", normalizedName, race.getId());
                    Category created = categoryRepository.save(Category.builder()
                            .categoryName(normalizedName)
                            .description("Auto-created from CSV import")
                            .race(race)
                            .build());
                    nameResolver.evict(eventId);
                    return created;
                });
    }

    @Override
    @Transactional(readOnly = true)
    public int countRaces(Long eventId) {
        return raceRepository.countByEventIdAndDeletedFalse(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public Long findRaceId(Long eventId, String rawName) {
        return raceRepository
                .findByRaceNameAndEventIdAndDeletedFalse(NameNormalizer.toStoredName(rawName), eventId)
                .map(Race::getId)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> categoryNames(Long raceId) {
        return categoryRepository.findByRaceId(raceId).stream()
                .map(Category::getCategoryName)
                .collect(Collectors.toSet());
    }
}
