package com.timekeeper.bibexpo.importer.batch;

import com.timekeeper.bibexpo.importer.model.enums.ImportMode;
import com.timekeeper.bibexpo.event.race.category.model.entity.Category;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.api.EventLimits;
import com.timekeeper.bibexpo.event.race.model.entity.Race;
import com.timekeeper.bibexpo.participant.api.ParticipantStore;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.event.api.EventStatsQuery;
import com.timekeeper.bibexpo.event.api.EventQuota;
import com.timekeeper.bibexpo.event.api.EventStore;
import com.timekeeper.bibexpo.event.api.RaceCategoryStore;
import com.timekeeper.bibexpo.user.api.UserDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class CsvItemProcessor implements ItemProcessor<CsvRow, ParticipantDDB> {

    private static final String BLANK_RACE = "Blank Race";
    private static final String BLANK_CATEGORY = "Blank Category";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final CsvRowValidator csvRowValidator;
    private final EventStore eventStore;
    private final ParticipantStore participantStore;
    private final UserDirectory userDirectory;
    private final RaceCategoryStore raceCategoryStore;
    private final EventQuota eventQuota;
    private final EventStatsQuery eventStatsQuery;

    @Value("#{jobParameters['eventId']}")
    private String eventIdParam;

    @Value("#{jobParameters['uploadedByUserId']}")
    private String userIdParam;

    @Value("#{jobParameters['mode']}")
    private String modeParam;

    private Long eventId;
    private Event event;
    private String username;
    private final Map<String, Race> raceCache = new HashMap<>();
    private final Map<String, Category> categoryCache = new HashMap<>();

    private boolean isAddOn;
    private long participantLimit;
    private long initialParticipantCount;
    private int processedThisJob;

    @Override
    public ParticipantDDB process(CsvRow row) {
        initIfNeeded();

        if (isAddOn && initialParticipantCount + processedThisJob >= participantLimit) {
            log.warn("Participant limit reached at row {} (limit={}, existing={}, added={})",
                    row.getRowNumber(), participantLimit, initialParticipantCount, processedThisJob);
            throw new BatchValidationException(
                    "Participant limit reached. This row and all following rows have been skipped.",
                    List.of(new ValidationError("participants", "Participant limit reached for this event.")),
                    BatchValidationException.TYPE_LIMIT_EXCEEDED);
        }

        List<ValidationError> errors = csvRowValidator.validate(row);
        if (!errors.isEmpty()) {
            log.warn("Skipping row {} due to validation errors: {}", row.getRowNumber(), errors);
            throw new BatchValidationException("Row " + row.getRowNumber() + " invalid", errors);
        }

        // An add-on only adds. Writing over a bib already on the roster would wipe its collection and
        // hand-outs, while the stock those hand-outs took stays off the shelf.
        // ponytail: one read per row, fine for walk-ins; look bibs up per chunk if large add-ons become common.
        if (isAddOn && participantStore.existsByEventAndBib(eventId, row.getBibNumber())) {
            throw new BatchValidationException("Row " + row.getRowNumber() + " holds a bib already registered",
                    List.of(new ValidationError("bibNumber",
                            "This bib number is already registered for this event, so the row was skipped.")),
                    BatchValidationException.TYPE_DUPLICATE_BIB);
        }

        try {
            ParticipantDDB result = mapCsvRowToParticipant(row);
            processedThisJob++;
            return result;
        } catch (Exception e) {
            log.warn("Processing error for row {}: {}", row.getRowNumber(), e.getMessage());
            throw new BatchValidationException(
                    "Row " + row.getRowNumber() + " processing failed: " + e.getMessage(),
                    List.of(),
                    BatchValidationException.TYPE_PROCESSING);
        }
    }

    private void initIfNeeded() {
        if (eventId == null) {
            eventId = Long.parseLong(eventIdParam);
            event = eventStore.requireById(eventId);
            if (userIdParam != null) {
                username = userDirectory.findUsername(Long.parseLong(userIdParam)).orElse("batch-import");
            } else {
                username = "batch-import";
            }

            isAddOn = ImportMode.ADD_ON.name().equals(modeParam);
            if (isAddOn) {
                EventLimits limits = eventQuota.forEvent(eventId);
                participantLimit = limits.maxParticipants();
                initialParticipantCount = eventStatsQuery.participantCount(eventId);
                processedThisJob = 0;
                log.info("ADD_ON import: existingCount={}, limit={}", initialParticipantCount, participantLimit);
            }
        }
    }

    private ParticipantDDB mapCsvRowToParticipant(CsvRow row) {
        String raceName = isBlank(row.getRaceName()) ? BLANK_RACE : row.getRaceName();
        String categoryName = isBlank(row.getCategoryName()) ? BLANK_CATEGORY : row.getCategoryName();

        Race race = getOrCreateRace(raceName);
        Category category = getOrCreateCategory(categoryName, race);

        String timestamp = LocalDateTime.now().format(FORMATTER);

        return ParticipantDDB.builder()
                .eventId(eventId.toString())
                .bibNumber(row.getBibNumber())
                .chipNumber(row.getChipNumber())
                .fullName(row.getFullName())
                .email(row.getEmail())
                .phoneNumber(row.getPhone())
                .dateOfBirth(row.getDateOfBirth())
                .age(row.getAge())
                .gender(row.getGender())
                .country(row.getCountry())
                .city(row.getCity())
                .raceId(race.getId().toString())
                .categoryId(category.getId().toString())
                .raceCategoryKey(ParticipantDDB.compositeKey(
                        race.getId().toString(), category.getId().toString()))
                .goodies(row.getGoodies() != null ? new HashMap<>(row.getGoodies()) : new HashMap<>())
                .additionalFields(row.getAdditionalFields() != null ? new HashMap<>(row.getAdditionalFields()) : new HashMap<>())
                .createdAt(timestamp)
                .createdBy(username)
                .updatedAt(timestamp)
                .updatedBy(username)
                .build();
    }

    private Race getOrCreateRace(String raceName) {
        Race cached = raceCache.get(raceName);
        if (cached != null) return cached;

        Race race = raceCategoryStore.findOrCreateRace(raceName, event);
        raceCache.put(raceName, race);
        return race;
    }

    private Category getOrCreateCategory(String categoryName, Race race) {
        String key = race.getId() + "|" + categoryName;
        Category cached = categoryCache.get(key);
        if (cached != null) return cached;

        Category category = raceCategoryStore.findOrCreateCategory(categoryName, eventId, race);
        categoryCache.put(key, category);
        return category;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
