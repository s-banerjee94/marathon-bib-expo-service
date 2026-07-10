package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.exception.EventLimitExceededException;
import com.timekeeper.bibexpo.model.dto.request.ImportMappingRequest;
import com.timekeeper.bibexpo.model.entity.Category;
import com.timekeeper.bibexpo.model.entity.EventLimit;
import com.timekeeper.bibexpo.model.entity.Race;
import com.timekeeper.bibexpo.model.enums.ImportMode;
import com.timekeeper.bibexpo.repository.CategoryRepository;
import com.timekeeper.bibexpo.repository.EventLimitRepository;
import com.timekeeper.bibexpo.repository.RaceRepository;
import com.timekeeper.bibexpo.repository.dynamodb.EventStatsDDBRepository;
import com.timekeeper.bibexpo.util.NameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Streams the uploaded CSV once before the batch job starts and rejects imports that would
 * breach participant, race, or category limits. Checks are fail-open on IO errors — the batch
 * job itself will surface any real parse failure.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CsvPreflightScanner {

    private static final String BLANK_RACE = "Blank Race";
    private static final String BLANK_CATEGORY = "Blank Category";

    private final CsvParserUtil csvParserUtil;
    private final RaceRepository raceRepository;
    private final CategoryRepository categoryRepository;
    private final EventStatsDDBRepository eventStatsRepo;
    private final EventLimitRepository eventLimitRepository;

    /**
     * Scans the CSV for resource limit violations before the batch job is launched.
     *
     * @throws EventLimitExceededException if the import would breach any configured limit
     */
    public void scan(Path csvPath, ImportMappingRequest mapping, Long eventId, ImportMode mode) {
        EventLimit limits = eventLimitRepository.findByEventId(eventId)
                .orElseGet(() -> EventLimit.builder().build());

        ScanResult result = scanCsv(csvPath, mapping);
        if (result == null) {
            return; // fail-open: let the batch job surface any real parse issue
        }

        checkParticipantLimit(result.rowCount, eventId, mode, limits);
        Map<String, Long> raceIdByRawName = checkRaceLimit(result.categoriesByRace.keySet(), eventId, limits);
        checkCategoryLimits(result.categoriesByRace, raceIdByRawName, limits);

        log.info("Pre-flight scan passed for event {}: {} rows, {} unique races", eventId, result.rowCount, result.categoriesByRace.size());
    }

    private void checkParticipantLimit(int csvRowCount, Long eventId, ImportMode mode, EventLimit limits) {
        long existingCount = (mode == ImportMode.ADD_ON)
                ? eventStatsRepo.getTotalParticipantCount(eventId.toString())
                : 0L;
        if (existingCount + csvRowCount > limits.getMaxParticipants()) {
            throw new EventLimitExceededException(
                    "This import would exceed the maximum number of participants allowed for this event.");
        }
    }

    private Map<String, Long> checkRaceLimit(Set<String> rawRaceNames, Long eventId, EventLimit limits) {
        int currentRaceCount = raceRepository.countByEventIdAndDeletedFalse(eventId);
        Map<String, Long> raceIdByRawName = new HashMap<>();
        int netNewRaces = 0;

        for (String rawName : rawRaceNames) {
            String normalized = NameNormalizer.toStoredName(rawName);
            Optional<Race> existing = raceRepository.findByRaceNameAndEventIdAndDeletedFalse(normalized, eventId);
            if (existing.isPresent()) {
                raceIdByRawName.put(rawName, existing.get().getId());
            } else {
                netNewRaces++;
                raceIdByRawName.put(rawName, null);
            }
        }

        if (currentRaceCount + netNewRaces > limits.getMaxRaces()) {
            throw new EventLimitExceededException(
                    "This import would exceed the maximum number of races allowed for this event.");
        }
        return raceIdByRawName;
    }

    private void checkCategoryLimits(Map<String, Set<String>> categoriesByRace,
                                     Map<String, Long> raceIdByRawName,
                                     EventLimit limits) {
        for (Map.Entry<String, Set<String>> entry : categoriesByRace.entrySet()) {
            Long raceId = raceIdByRawName.get(entry.getKey());

            Set<String> existingNormalized = raceId != null
                    ? categoryRepository.findByRaceId(raceId).stream()
                            .map(Category::getCategoryName)
                            .collect(Collectors.toSet())
                    : Set.of();

            Set<String> csvNormalized = entry.getValue().stream()
                    .map(NameNormalizer::toStoredName)
                    .collect(Collectors.toSet());

            int currentCount = existingNormalized.size();
            long netNew = csvNormalized.stream().filter(n -> !existingNormalized.contains(n)).count();

            if (currentCount + netNew > limits.getMaxCategoriesPerRace()) {
                throw new EventLimitExceededException(
                        "This import would exceed the maximum number of categories allowed per race.");
            }
        }
    }

    private ScanResult scanCsv(Path csvPath, ImportMappingRequest mapping) {
        int rowCount = 0;
        Map<String, Set<String>> categoriesByRace = new HashMap<>();

        try (InputStream is = new FileInputStream(csvPath.toFile());
             CsvParseStream stream = csvParserUtil.openStream(is, mapping)) {

            CsvRow row;
            while ((row = stream.nextRow()) != null) {
                rowCount++;
                String raceName = isBlank(row.getRaceName()) ? BLANK_RACE : row.getRaceName();
                String catName = isBlank(row.getCategoryName()) ? BLANK_CATEGORY : row.getCategoryName();
                categoriesByRace.computeIfAbsent(raceName, k -> new HashSet<>()).add(catName);
            }
        } catch (IOException e) {
            log.warn("Pre-flight CSV scan could not read file {}: {}", csvPath, e.getMessage());
            return null;
        }

        return new ScanResult(rowCount, categoriesByRace);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private record ScanResult(int rowCount, Map<String, Set<String>> categoriesByRace) {}
}
