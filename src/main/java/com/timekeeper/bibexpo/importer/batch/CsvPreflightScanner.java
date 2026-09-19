package com.timekeeper.bibexpo.importer.batch;

import com.timekeeper.bibexpo.event.limit.exception.EventLimitExceededException;
import com.timekeeper.bibexpo.importer.model.dto.request.ImportMappingRequest;
import com.timekeeper.bibexpo.event.api.EventLimits;
import com.timekeeper.bibexpo.importer.model.enums.ImportMode;
import com.timekeeper.bibexpo.event.api.EventStatsQuery;
import com.timekeeper.bibexpo.event.api.EventQuota;
import com.timekeeper.bibexpo.event.api.GoodieEntitlement;
import com.timekeeper.bibexpo.event.api.RaceCategoryStore;
import com.timekeeper.bibexpo.participant.api.ParticipantStore;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.shared.util.NameNormalizer;
import com.timekeeper.bibexpo.shared.util.TextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Streams the uploaded CSV once before the batch job starts and rejects imports that would breach
 * participant, race, or category limits, or that mark a column holding far too many different values
 * as goodies. Checks are fail-open on IO errors — the batch job itself will surface any real parse
 * failure.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CsvPreflightScanner {

    private static final String BLANK_RACE = "Blank Race";
    private static final String BLANK_CATEGORY = "Blank Category";

    // A goody is a handful of sizes or a yes/no, never a column of free text. More values than this and
    // the wrong column was marked as goodies on the import screen, which is worth stopping for: the
    // counters are keyed by value, and the check screen asks the organizer to map every one of them.
    private static final int MAX_VALUES_PER_GOODIE = 50;

    private final CsvParserUtil csvParserUtil;
    private final RaceCategoryStore raceCategoryStore;
    private final EventStatsQuery eventStatsQuery;
    private final EventQuota eventQuota;
    private final ParticipantStore participantStore;

    /**
     * Scans the CSV for resource limit violations before the batch job is launched.
     *
     * @throws EventLimitExceededException if the import would breach any configured limit
     * @throws InvalidUserDataException if a column marked as goodies holds more different values than
     *                                  a goody may have
     */
    public void scan(Path csvPath, ImportMappingRequest mapping, Long eventId, ImportMode mode) {
        EventLimits limits = eventQuota.forEvent(eventId);

        Map<String, ScannedRow> rowsByBib = scanCsv(csvPath, mapping);
        if (rowsByBib == null) {
            return; // fail-open: let the batch job surface any real parse issue
        }
        // An add-on skips a bib already registered, so that row adds no participant, race or category.
        if (mode == ImportMode.ADD_ON) {
            rowsByBib.keySet().removeAll(participantStore.findExistingBibs(eventId, rowsByBib.keySet()));
        }
        Map<String, Set<String>> categoriesByRace = new HashMap<>();
        rowsByBib.values().forEach(row ->
                categoriesByRace.computeIfAbsent(row.race(), k -> new HashSet<>()).add(row.category()));

        checkGoodieValues(rowsByBib.values(), eventId, mode);
        checkParticipantLimit(rowsByBib.size(), eventId, mode, limits);
        Map<String, Long> raceIdByRawName = checkRaceLimit(categoriesByRace.keySet(), eventId, limits);
        checkCategoryLimits(categoriesByRace, raceIdByRawName, limits);

        log.info("Pre-flight scan passed for event {}: {} rows, {} unique races", eventId, rowsByBib.size(),
                categoriesByRace.size());
    }

    /**
     * Refuses a column marked as goodies that holds more different values than a goody plausibly has.
     * Values are counted as the counters store them, spelling for spelling, because that is what the
     * check screen asks the organizer to map; an add-on counts the values the event already carries too.
     */
    private void checkGoodieValues(Collection<ScannedRow> rows, Long eventId, ImportMode mode) {
        Map<String, Set<String>> valuesByGoodie = new LinkedHashMap<>();
        Map<String, String> goodieNames = new LinkedHashMap<>();
        if (mode == ImportMode.ADD_ON) {
            for (GoodieEntitlement entitlement : eventStatsQuery.entitlements(eventId)) {
                collectValue(valuesByGoodie, goodieNames, entitlement.goodieName(), entitlement.value());
            }
        }
        rows.forEach(row -> row.goodies().forEach(
                (goodie, value) -> collectValue(valuesByGoodie, goodieNames, goodie, value)));

        List<String> tooMany = valuesByGoodie.entrySet().stream()
                .filter(entry -> entry.getValue().size() > MAX_VALUES_PER_GOODIE)
                .map(entry -> "\"" + goodieNames.get(entry.getKey()) + "\" has " + entry.getValue().size())
                .toList();
        if (!tooMany.isEmpty()) {
            log.warn("Import for event {} refused: {} column(s) hold more than {} distinct values",
                    eventId, tooMany.size(), MAX_VALUES_PER_GOODIE);
            throw new InvalidUserDataException("A goodies column may hold at most " + MAX_VALUES_PER_GOODIE
                    + " different values, but " + TextUtils.joinAsSentence(tooMany)
                    + ", so it was most likely marked as goodies by mistake; unmark it and import again.");
        }
    }

    private static void collectValue(Map<String, Set<String>> valuesByGoodie, Map<String, String> goodieNames,
                                     String goodieName, String value) {
        String key = TextUtils.toMatchKey(goodieName);
        goodieNames.putIfAbsent(key, goodieName);
        valuesByGoodie.computeIfAbsent(key, ignored -> new HashSet<>()).add(TextUtils.nullSafe(value));
    }

    private void checkParticipantLimit(int csvRowCount, Long eventId, ImportMode mode, EventLimits limits) {
        long existingCount = (mode == ImportMode.ADD_ON)
                ? eventStatsQuery.participantCount(eventId)
                : 0L;
        if (existingCount + csvRowCount > limits.maxParticipants()) {
            throw new EventLimitExceededException(
                    "This import would exceed the maximum number of participants allowed for this event.");
        }
    }

    private Map<String, Long> checkRaceLimit(Set<String> rawRaceNames, Long eventId, EventLimits limits) {
        int currentRaceCount = raceCategoryStore.countRaces(eventId);
        Map<String, Long> raceIdByRawName = new HashMap<>();
        int netNewRaces = 0;

        for (String rawName : rawRaceNames) {
            Long existingId = raceCategoryStore.findRaceId(eventId, rawName);
            if (existingId == null) {
                netNewRaces++;
            }
            raceIdByRawName.put(rawName, existingId);
        }

        if (currentRaceCount + netNewRaces > limits.maxRaces()) {
            throw new EventLimitExceededException(
                    "This import would exceed the maximum number of races allowed for this event.");
        }
        return raceIdByRawName;
    }

    private void checkCategoryLimits(Map<String, Set<String>> categoriesByRace,
                                     Map<String, Long> raceIdByRawName,
                                     EventLimits limits) {
        for (Map.Entry<String, Set<String>> entry : categoriesByRace.entrySet()) {
            Long raceId = raceIdByRawName.get(entry.getKey());

            Set<String> existingNormalized = raceId != null
                    ? raceCategoryStore.categoryNames(raceId)
                    : Set.of();

            Set<String> csvNormalized = entry.getValue().stream()
                    .map(NameNormalizer::toStoredName)
                    .collect(Collectors.toSet());

            int currentCount = existingNormalized.size();
            long netNew = csvNormalized.stream().filter(n -> !existingNormalized.contains(n)).count();

            if (currentCount + netNew > limits.maxCategoriesPerRace()) {
                throw new EventLimitExceededException(
                        "This import would exceed the maximum number of categories allowed per race.");
            }
        }
    }

    // The job reads a repeated bib once, as its first row, so that is the only row counted for it.
    private Map<String, ScannedRow> scanCsv(Path csvPath, ImportMappingRequest mapping) {
        Map<String, ScannedRow> rowsByBib = new LinkedHashMap<>();

        try (InputStream is = new FileInputStream(csvPath.toFile());
             CsvParseStream stream = csvParserUtil.openStream(is, mapping)) {

            CsvRow row;
            while ((row = stream.nextRow()) != null) {
                String raceName = isBlank(row.getRaceName()) ? BLANK_RACE : row.getRaceName();
                String catName = isBlank(row.getCategoryName()) ? BLANK_CATEGORY : row.getCategoryName();
                rowsByBib.putIfAbsent(row.getBibNumber(), new ScannedRow(raceName, catName, row.getGoodies()));
            }
        } catch (IOException e) {
            log.warn("Pre-flight CSV scan could not read file {}: {}", csvPath, e.getMessage());
            return null;
        }

        return rowsByBib;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private record ScannedRow(String race, String category, Map<String, String> goodies) {

        ScannedRow {
            goodies = goodies == null ? Map.of() : goodies;
        }
    }
}
