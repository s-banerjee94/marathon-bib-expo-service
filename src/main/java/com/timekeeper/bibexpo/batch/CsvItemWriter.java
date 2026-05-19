package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Category;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.Race;
import com.timekeeper.bibexpo.repository.CategoryRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.RaceRepository;
import com.timekeeper.bibexpo.repository.dynamodb.ParticipantDDBRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class CsvItemWriter implements ItemWriter<ParticipantDDB> {

    private final ParticipantDDBRepository repository;
    private final EventRepository eventRepository;
    private final RaceRepository raceRepository;
    private final CategoryRepository categoryRepository;

    @Value("#{jobParameters['eventId']}")
    private String eventId;

    @Override
    public void write(Chunk<? extends ParticipantDDB> chunk) {
        Long eventIdLong = Long.parseLong(eventId);
        Event event = eventRepository.findById(eventIdLong)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));

        Map<String, Race> raceCache = findOrCreateRaces(chunk, eventIdLong, event);
        Map<String, Category> categoryCache = findOrCreateCategories(chunk, raceCache);
        enrichParticipants(chunk, raceCache, categoryCache);

        repository.batchSave(new ArrayList<>(chunk.getItems()));
        log.debug("Written chunk of {} participants to DynamoDB", chunk.size());
    }

    private Map<String, Race> findOrCreateRaces(Chunk<? extends ParticipantDDB> chunk, Long eventIdLong, Event event) {
        Set<String> raceNames = chunk.getItems().stream()
                .map(ParticipantDDB::getRaceName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());

        Map<String, Race> raceCache = new HashMap<>();
        for (String raceName : raceNames) {
            Race race = raceRepository.findByRaceNameAndEventIdAndDeletedFalse(raceName, eventIdLong)
                    .orElseGet(() -> raceRepository.save(Race.builder()
                            .raceName(raceName)
                            .raceDescription("Auto-created from CSV import")
                            .event(event)
                            .deleted(false)
                            .build()));
            raceCache.put(raceName, race);
        }
        return raceCache;
    }

    private Map<String, Category> findOrCreateCategories(Chunk<? extends ParticipantDDB> chunk, Map<String, Race> raceCache) {
        Map<String, Category> categoryCache = new HashMap<>();
        chunk.getItems().stream()
                .filter(p -> p.getRaceName() != null && !p.getRaceName().isBlank()
                        && p.getCategoryName() != null && !p.getCategoryName().isBlank())
                .collect(Collectors.toMap(
                        p -> p.getRaceName() + "|" + p.getCategoryName(),
                        p -> p,
                        (a, b) -> a))
                .values()
                .forEach(p -> {
                    Race race = raceCache.get(p.getRaceName());
                    String key = p.getRaceName() + "|" + p.getCategoryName();
                    Category category = categoryRepository.findByCategoryNameAndRaceId(p.getCategoryName(), race.getId())
                            .orElseGet(() -> categoryRepository.save(Category.builder()
                                    .categoryName(p.getCategoryName())
                                    .description("Auto-created from CSV import")
                                    .race(race)
                                    .build()));
                    categoryCache.put(key, category);
                });
        return categoryCache;
    }

    private void enrichParticipants(Chunk<? extends ParticipantDDB> chunk,
                                     Map<String, Race> raceCache, Map<String, Category> categoryCache) {
        chunk.getItems().forEach(p -> {
            if (p.getRaceName() != null && !p.getRaceName().isBlank()) {
                Race race = raceCache.get(p.getRaceName());
                p.setRaceId(race.getId().toString());
                if (p.getCategoryName() != null && !p.getCategoryName().isBlank()) {
                    Category category = categoryCache.get(p.getRaceName() + "|" + p.getCategoryName());
                    if (category != null) {
                        p.setCategoryId(category.getId().toString());
                    }
                }
            }
        });
    }
}
