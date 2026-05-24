package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Category;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.Race;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.util.CsvRow;
import com.timekeeper.bibexpo.validator.CsvRowValidator;
import com.timekeeper.bibexpo.validator.ValidationError;
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
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final BatchReferenceDataService referenceDataService;

    @Value("#{jobParameters['eventId']}")
    private String eventIdParam;

    @Value("#{jobParameters['uploadedByUserId']}")
    private String userIdParam;

    private Long eventId;
    private Event event;
    private String username;
    private final Map<String, Race> raceCache = new HashMap<>();
    private final Map<String, Category> categoryCache = new HashMap<>();

    @Override
    public ParticipantDDB process(CsvRow row) {
        initIfNeeded();

        List<ValidationError> errors = csvRowValidator.validate(row);
        if (!errors.isEmpty()) {
            log.warn("Skipping row {} due to validation errors: {}", row.getRowNumber(), errors);
            throw new BatchValidationException("Row " + row.getRowNumber() + " invalid", errors);
        }

        try {
            return mapCsvRowToParticipant(row);
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
            event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new EventNotFoundException());
            if (userIdParam != null) {
                User user = userRepository.findById(Long.parseLong(userIdParam)).orElse(null);
                username = user != null ? user.getUsername() : "batch-import";
            } else {
                username = "batch-import";
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
                .raceName(race.getRaceName())
                .categoryId(category.getId().toString())
                .categoryName(category.getCategoryName())
                .goodies(row.getGoodies() != null ? new HashMap<>(row.getGoodies()) : new HashMap<>())
                .createdAt(timestamp)
                .createdBy(username)
                .updatedAt(timestamp)
                .updatedBy(username)
                .build();
    }

    private Race getOrCreateRace(String raceName) {
        Race cached = raceCache.get(raceName);
        if (cached != null) return cached;

        Race race = referenceDataService.findOrCreateRace(raceName, eventId, event);
        raceCache.put(raceName, race);
        return race;
    }

    private Category getOrCreateCategory(String categoryName, Race race) {
        String key = race.getId() + "|" + categoryName;
        Category cached = categoryCache.get(key);
        if (cached != null) return cached;

        Category category = referenceDataService.findOrCreateCategory(categoryName, race);
        categoryCache.put(key, category);
        return category;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
