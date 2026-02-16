package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.*;
import com.timekeeper.bibexpo.validator.ValidationError;
import tools.jackson.databind.json.JsonMapper;
import com.timekeeper.bibexpo.model.dto.request.CreateCategoryRequest;
import com.timekeeper.bibexpo.model.dto.request.CreateParticipantRequest;
import com.timekeeper.bibexpo.model.dto.request.CreateRaceRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateParticipantRequest;
import com.timekeeper.bibexpo.model.dto.response.CategoryResponse;
import com.timekeeper.bibexpo.model.dto.response.DeleteParticipantsResponse;
import com.timekeeper.bibexpo.model.dto.response.ErrorSummary;
import com.timekeeper.bibexpo.model.dto.response.ImportError;
import com.timekeeper.bibexpo.model.dto.response.ImportErrorListResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportJobListResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportJobResponse;
import com.timekeeper.bibexpo.model.dto.response.ImportParticipantsResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantListResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.RaceResponse;
import com.timekeeper.bibexpo.model.dynamodb.ImportErrorDDB;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.*;
import com.timekeeper.bibexpo.model.enums.ExportField;
import com.timekeeper.bibexpo.model.enums.SearchType;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.ImportJobRepository;
import com.timekeeper.bibexpo.service.CategoryService;
import com.timekeeper.bibexpo.service.EventService;
import com.timekeeper.bibexpo.service.ParticipantService;
import com.timekeeper.bibexpo.service.RaceService;
import com.timekeeper.bibexpo.service.util.DynamoDBPaginationCodec;
import com.timekeeper.bibexpo.service.validator.DistributionValidator;
import com.timekeeper.bibexpo.util.CsvParseResult;
import com.timekeeper.bibexpo.util.CsvParserUtil;
import com.timekeeper.bibexpo.util.CsvRow;
import com.timekeeper.bibexpo.validator.CsvRowValidator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantServiceImpl implements ParticipantService {

    public static final String EVENT_NOT_FOUND_WITH_ID = "Event not found with ID: ";
    public static final String BATCH_WRITE_ERROR = "BATCH_WRITE_ERROR";
    public static final String FAILED_TO_DECODE_PAGINATION_KEY = "Failed to decode pagination key";
    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final EventService eventService;
    private final EventRepository eventRepository;
    private final RaceService raceService;
    private final CategoryService categoryService;
    private final CsvParserUtil csvParserUtil;
    private final CsvRowValidator csvRowValidator;
    private final JsonMapper objectMapper;
    private final ImportJobRepository importJobRepository;
    private final DistributionValidator validator;
    private final DynamoDBPaginationCodec paginationCodec;

    private DynamoDbTable<ParticipantDDB> participantTable;
    private DynamoDbTable<ImportErrorDDB> importErrorTable;

    private static final int BATCH_SIZE = 25;
    private static final int ERROR_BATCH_SIZE = 25;
    private static final int ERROR_RESPONSE_LIMIT = 100;
    private static final int ERROR_TTL_DAYS = 30;
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @PostConstruct
    public void init() {
        this.participantTable = dynamoDbEnhancedClient.table(
                "marathon-participants",
                TableSchema.fromBean(ParticipantDDB.class)
        );
        this.importErrorTable = dynamoDbEnhancedClient.table(
                "marathon-import-errors",
                TableSchema.fromBean(ImportErrorDDB.class)
        );
    }

    @Override
    public ParticipantResponse createParticipant(Long eventId, CreateParticipantRequest request, User currentUser) {
        log.info("Creating participant with BIB {} for event ID: {} by user: {}",
                request.getBibNumber(), eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        ParticipantDDB existingParticipant = participantTable.getItem(
                Key.builder()
                        .partitionValue(eventId.toString())
                        .sortValue(request.getBibNumber())
                        .build()
        );

        if (existingParticipant != null) {
            throw new IllegalArgumentException("Participant with BIB number " + request.getBibNumber() + " already exists");
        }

        RaceResponse race = raceService.getRaceById(eventId, request.getRaceId(), currentUser);
        CategoryResponse category = categoryService.getCategoryById(eventId, request.getRaceId(),
                request.getCategoryId(), currentUser);

        String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);

        ParticipantDDB participant = ParticipantDDB.builder()
                .eventId(eventId.toString())
                .bibNumber(request.getBibNumber())
                .chipNumber(request.getChipNumber())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .dateOfBirth(request.getDateOfBirth())
                .age(request.getAge())
                .gender(request.getGender())
                .country(request.getCountry())
                .city(request.getCity())
                .raceId(request.getRaceId().toString())
                .raceName(race.getRaceName())
                .categoryId(request.getCategoryId().toString())
                .categoryName(category.getCategoryName())
                .bibCollectedAt(request.getBibCollectedAt())
                .goodies(request.getGoodies() != null ? new HashMap<>(request.getGoodies()) : new HashMap<>())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .notes(request.getNotes())
                .createdAt(timestamp)
                .createdBy(currentUser.getUsername())
                .updatedAt(timestamp)
                .updatedBy(currentUser.getUsername())
                .build();

        participantTable.putItem(participant);

        log.info("Successfully created participant with BIB {} for event ID: {}", request.getBibNumber(), eventId);

        return mapParticipantToResponse(participant);
    }

    @Override
    @Transactional
    public ImportParticipantsResponse importParticipantsFromCsv(Long eventId, MultipartFile file, User currentUser) {
        String importId = UUID.randomUUID().toString();
        log.info("Starting CSV import {} for event ID: {} by user: {}", importId, eventId, currentUser.getUsername());

        validateFile(file);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        ImportJob importJob = ImportJob.builder()
                .importId(importId)
                .eventId(eventId)
                .eventName(event.getEventName())
                .fileName(file.getOriginalFilename())
                .totalRows(0)
                .successCount(0)
                .failureCount(0)
                .status(ImportJob.ImportStatus.IN_PROGRESS)
                .importedBy(currentUser.getId())
                .build();
        importJobRepository.save(importJob);

        CsvParseResult parseResult;
        try {
            parseResult = csvParserUtil.parseCsv(file.getInputStream());
        } catch (IOException e) {
            importJob.setStatus(ImportJob.ImportStatus.FAILED);
            importJobRepository.save(importJob);
            throw new InvalidCsvFormatException("Failed to parse CSV file", e);
        } catch (IllegalArgumentException e) {
            importJob.setStatus(ImportJob.ImportStatus.FAILED);
            importJobRepository.save(importJob);
            throw new InvalidCsvFormatException("Invalid CSV format: " + e.getMessage(), e);
        }

        log.info("CSV Import Summary - Total rows: {}, Goodies columns: {}",
                parseResult.getTotalRows(), parseResult.getGoodiesColumns());

        parseResult.getRows().stream().limit(3).forEach(row ->
                log.info("Sample Row {}: BIB={}, Goodies={}",
                        row.getRowNumber(), row.getBibNumber(), row.getGoodies()));

        int deletedCount = deleteAllParticipantsForEvent(eventId);
        log.info("Deleted {} existing participants for event ID: {} before fresh import", deletedCount, eventId);

        updateEventGoodies(event, parseResult.getGoodiesColumns());

        Map<String, Race> raceMap = buildRaceMap(eventId, currentUser);
        Map<String, Category> categoryMap = buildCategoryMap(raceMap, currentUser);

        List<ImportError> allErrors = new ArrayList<>();
        List<ParticipantDDB> participantsToImport = new ArrayList<>();
        Set<String> bibNumbers = new HashSet<>();

        for (CsvRow csvRow : parseResult.getRows()) {
            List<ValidationError> rowErrors = csvRowValidator.validate(csvRow);

            if (!rowErrors.isEmpty()) {
                for (ValidationError validationError : rowErrors) {
                    allErrors.add(createImportError(csvRow.getRowNumber(), "VALIDATION_ERROR",
                            validationError.getField(), validationError.getMessage()));
                }
            } else if (bibNumbers.contains(csvRow.getBibNumber())) {
                allErrors.add(createImportError(csvRow.getRowNumber(), "DUPLICATE_BIB", "bibNumber",
                        String.format("Duplicate BIB No. '%s' in CSV", csvRow.getBibNumber())));
            } else {
                bibNumbers.add(csvRow.getBibNumber());
                try {
                    ParticipantDDB participant = mapCsvRowToParticipant(csvRow, eventId, raceMap, categoryMap, currentUser);
                    participantsToImport.add(participant);
                } catch (Exception e) {
                    allErrors.add(createImportError(csvRow.getRowNumber(), "PROCESSING_ERROR", null,
                            "Failed to process - " + e.getMessage()));
                }
            }
        }

        int successCount = bulkWriteParticipants(participantsToImport, allErrors);

        bulkWriteImportErrors(importId, allErrors);

        ErrorSummary errorSummary = buildErrorSummary(allErrors);

        importJob.setTotalRows(parseResult.getTotalRows());
        importJob.setSuccessCount(successCount);
        importJob.setFailureCount(allErrors.size());
        importJob.setStatus(ImportJob.ImportStatus.COMPLETED);
        importJob.setErrorSummary(serializeErrorSummary(errorSummary));
        importJob.setGoodiesDetected(String.join(",", parseResult.getGoodiesColumns()));
        importJobRepository.save(importJob);

        log.info("CSV import {} completed for event ID: {}. Deleted: {}, Success: {}, Failed: {}",
                importId, eventId, deletedCount, successCount, allErrors.size());

        String message = String.format("Fresh import completed. Deleted %d old participants, imported %d new participants. Use GET /api/events/%d/imports/%s for details.",
                deletedCount, successCount, eventId, importId);

        return ImportParticipantsResponse.builder()
                .importId(importId)
                .status(ImportJob.ImportStatus.COMPLETED.name())
                .totalRows(parseResult.getTotalRows())
                .successCount(successCount)
                .failureCount(allErrors.size())
                .message(message)
                .build();
    }

    @Override
    public ParticipantListResponse getParticipantsByEvent(Long eventId, Integer limit, String lastEvaluatedKey, User currentUser) {
        log.info("Fetching participants for event ID: {} with limit: {} by user: {}",
                eventId, limit, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(eventId.toString()).build()
        );

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit != null ? limit : 50);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            try {
                Map<String, AttributeValue> exclusiveStartKey = paginationCodec.decode(lastEvaluatedKey);
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            } catch (Exception e) {
                log.error(FAILED_TO_DECODE_PAGINATION_KEY, e);
                throw new InvalidUserDataException("Invalid pagination key");
            }
        }

        Page<ParticipantDDB> page = participantTable.query(requestBuilder.build()).stream().findFirst().orElse(null);

        if (page == null) {
            return ParticipantListResponse.builder()
                    .participants(Collections.emptyList())
                    .count(0)
                    .hasMore(false)
                    .build();
        }

        List<ParticipantResponse> participants = page.items().stream()
                .map(this::mapParticipantToResponse)
                .toList();

        String newLastEvaluatedKey = null;
        if (page.lastEvaluatedKey() != null && !page.lastEvaluatedKey().isEmpty()) {
            newLastEvaluatedKey = paginationCodec.encode(page.lastEvaluatedKey());
        }

        return ParticipantListResponse.builder()
                .participants(participants)
                .lastEvaluatedKey(newLastEvaluatedKey)
                .count(participants.size())
                .hasMore(newLastEvaluatedKey != null)
                .build();
    }

    @Override
    public ParticipantResponse getParticipantByBibNumber(Long eventId, String bibNumber, User currentUser) {
        log.info("Fetching participant with bib {} for event ID: {} by user: {}",
                bibNumber, eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);

        ParticipantDDB participant = participantTable.getItem(
                Key.builder()
                        .partitionValue(eventId.toString())
                        .sortValue(bibNumber)
                        .build()
        );

        if (participant == null) {
            throw new ParticipantNotFoundException(eventId.toString(), bibNumber);
        }

        log.info("Retrieved participant BIB {} from DynamoDB with {} goodies: {}",
                bibNumber, participant.getGoodies() != null ? participant.getGoodies().size() : 0,
                participant.getGoodies());

        return mapParticipantToResponse(participant);
    }

    @Override
    public ParticipantResponse updateParticipant(Long eventId, String bibNumber,
                                                  UpdateParticipantRequest request, User currentUser) {

        log.info("Updating participant BIB {} for event ID: {} by user: {}",
                bibNumber, eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);

        ParticipantDDB participant = participantTable.getItem(
                Key.builder()
                        .partitionValue(eventId.toString())
                        .sortValue(bibNumber)
                        .build()
        );

        if (participant == null) {
            throw new ParticipantNotFoundException(eventId.toString(), bibNumber);
        }

        boolean bibNumberChanged = false;
        String newBibNumber = bibNumber;

        if (request.getNewBibNumber() != null && !request.getNewBibNumber().equals(bibNumber)) {
            newBibNumber = request.getNewBibNumber();
            bibNumberChanged = true;

            ParticipantDDB existingWithNewBib = participantTable.getItem(
                    Key.builder()
                            .partitionValue(eventId.toString())
                            .sortValue(newBibNumber)
                            .build()
            );

            if (existingWithNewBib != null) {
                throw new IllegalArgumentException(
                        "Participant with BIB number " + newBibNumber + " already exists");
            }

            log.warn("BIB number change requested: {} -> {} for event {}",
                    bibNumber, newBibNumber, eventId);
        }

        if (request.getRaceId() != null || request.getCategoryId() != null) {
            updateRaceAndCategory(participant, request, eventId, currentUser);
        }

        updateIfNotNull(request.getChipNumber(), participant::setChipNumber);
        updateIfNotNull(request.getFullName(), participant::setFullName);
        updateIfNotNull(request.getEmail(), participant::setEmail);
        updateIfNotNull(request.getPhoneNumber(), participant::setPhoneNumber);
        updateIfNotNull(request.getDateOfBirth(), participant::setDateOfBirth);
        updateIfNotNull(request.getAge(), participant::setAge);
        updateIfNotNull(request.getGender(), participant::setGender);
        updateIfNotNull(request.getCountry(), participant::setCountry);
        updateIfNotNull(request.getCity(), participant::setCity);
        updateIfNotNull(request.getEmergencyContactName(), participant::setEmergencyContactName);
        updateIfNotNull(request.getEmergencyContactPhone(), participant::setEmergencyContactPhone);
        updateIfNotNull(request.getNotes(), participant::setNotes);
        updateIfNotNull(request.getBibCollectedAt(), participant::setBibCollectedAt);

        String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        participant.setUpdatedAt(timestamp);
        participant.setUpdatedBy(currentUser.getUsername());

        if (bibNumberChanged) {
            participantTable.deleteItem(
                    Key.builder()
                            .partitionValue(eventId.toString())
                            .sortValue(bibNumber)
                            .build()
            );

            participant.setBibNumber(newBibNumber);

            participantTable.putItem(participant);

            log.info("BIB number changed from {} to {} for event ID: {}",
                    bibNumber, newBibNumber, eventId);
        } else {
            participantTable.putItem(participant);

            log.info("Successfully updated participant BIB {} for event ID: {}", bibNumber, eventId);
        }

        return mapParticipantToResponse(participant);
    }

    @Override
    public Long getParticipantCount(Long eventId, User currentUser) {
        log.info("Counting participants for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(eventId.toString()).build()
        );

        long count = participantTable.query(queryConditional).stream()
                .mapToLong(page -> page.items().size())
                .sum();

        log.info("Found {} participants for event ID: {}", count, eventId);
        return count;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidCsvFormatException("CSV file is required");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new InvalidCsvFormatException("File must be a CSV file with .csv extension");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidCsvFormatException(
                    String.format("File size exceeds maximum allowed size of %d MB", MAX_FILE_SIZE / (1024 * 1024)));
        }
    }


    private void updateEventGoodies(Event event, List<String> goodiesColumns) {
        try {
            String goodiesJson = objectMapper.writeValueAsString(goodiesColumns);
            event.setEventGoodies(goodiesJson);
            eventRepository.save(event);
            log.info("Updated event ID: {} with goodies: {}", event.getId(), goodiesJson);
        } catch (Exception e) {
            throw new CsvImportException("Failed to serialize goodies to JSON", e);
        }
    }

    private Map<String, Race> buildRaceMap(Long eventId, User currentUser) {
        List<RaceResponse> races =
                raceService.getRacesByEventId(eventId, currentUser);

        return races.stream()
                .collect(Collectors.toMap(
                        RaceResponse::getRaceName,
                        r -> raceService.findByEventIdAndRaceName(eventId, r.getRaceName(), currentUser)
                ));
    }

    private Map<String, Category> buildCategoryMap(Map<String, Race> raceMap, User currentUser) {
        Map<String, Category> categoryMap = new HashMap<>();

        for (Race race : raceMap.values()) {
            List<CategoryResponse> categories =
                    categoryService.getCategoriesByRaceId(race.getEvent().getId(), race.getId(), null, currentUser);

            for (CategoryResponse categoryResponse : categories) {
                String key = race.getId() + ":" + categoryResponse.getCategoryName();
                Category category = categoryService.findByRaceIdAndCategoryName(
                        race.getId(), categoryResponse.getCategoryName(), currentUser);
                categoryMap.put(key, category);
            }
        }

        return categoryMap;
    }

    private Race getOrCreateRace(String raceName, Long eventId, Map<String, Race> raceMap, User currentUser) {
        Race race = raceMap.get(raceName);

        if (race == null) {
            log.info("Race '{}' not found for event ID: {}. Creating new race.", raceName, eventId);

            CreateRaceRequest createRaceRequest = CreateRaceRequest.builder()
                    .raceName(raceName)
                    .raceDescription("Auto-created from CSV import")
                    .build();

            raceService.createRace(eventId, createRaceRequest, currentUser);
            race = raceService.findByEventIdAndRaceName(eventId, raceName, currentUser);
            raceMap.put(raceName, race);

            log.info("Created new race '{}' with ID: {}", raceName, race.getId());
        }

        return race;
    }

    private Category getOrCreateCategory(String categoryName, Race race, String categoryKey,
                                          Map<String, Category> categoryMap, User currentUser) {
        Category category = categoryMap.get(categoryKey);

        if (category == null) {
            log.info("Category '{}' not found for race ID: {}. Creating new category.", categoryName, race.getId());

            CreateCategoryRequest createCategoryRequest = CreateCategoryRequest.builder()
                    .categoryName(categoryName)
                    .build();

            categoryService.createCategory(race.getEvent().getId(), race.getId(), createCategoryRequest, currentUser);
            category = categoryService.findByRaceIdAndCategoryName(race.getId(), categoryName, currentUser);
            categoryMap.put(categoryKey, category);

            log.info("Created new category '{}' with ID: {}", categoryName, category.getId());
        }

        return category;
    }

    private ParticipantDDB mapCsvRowToParticipant(CsvRow csvRow, Long eventId,
                                                    Map<String, Race> raceMap,
                                                    Map<String, Category> categoryMap,
                                                    User currentUser) {
        String raceName = isBlank(csvRow.getRaceName()) ? "Blank Race" : csvRow.getRaceName();
        String categoryName = isBlank(csvRow.getCategoryName()) ? "Blank Category" : csvRow.getCategoryName();

        Race race = getOrCreateRace(raceName, eventId, raceMap, currentUser);

        String categoryKey = race.getId() + ":" + categoryName;
        Category category = getOrCreateCategory(categoryName, race, categoryKey, categoryMap, currentUser);

        String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);

        return ParticipantDDB.builder()
                .eventId(eventId.toString())
                .bibNumber(csvRow.getBibNumber())
                .chipNumber(csvRow.getChipNumber())
                .fullName(csvRow.getFullName())
                .email(csvRow.getEmail())
                .phoneNumber(csvRow.getPhone())
                .dateOfBirth(csvRow.getDateOfBirth())
                .age(csvRow.getAge())
                .gender(csvRow.getGender())
                .country(csvRow.getCountry())
                .city(csvRow.getCity())
                .raceId(race.getId().toString())
                .raceName(race.getRaceName())
                .categoryId(category.getId().toString())
                .categoryName(category.getCategoryName())
                .goodies(csvRow.getGoodies() != null ? new HashMap<>(csvRow.getGoodies()) : new HashMap<>())
                .createdAt(timestamp)
                .createdBy(currentUser.getUsername())
                .updatedAt(timestamp)
                .updatedBy(currentUser.getUsername())
                .build();
    }

    private int bulkWriteParticipants(List<ParticipantDDB> participants, List<ImportError> errors) {
        if (participants.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        List<List<ParticipantDDB>> batches = partitionList(participants);

        for (int i = 0; i < batches.size(); i++) {
            List<ParticipantDDB> batch = batches.get(i);
            log.info("Writing batch {} of {} ({} items)", i + 1, batches.size(), batch.size());

            try {
                WriteBatch.Builder<ParticipantDDB> batchBuilder = WriteBatch.builder(ParticipantDDB.class)
                        .mappedTableResource(participantTable);

                for (ParticipantDDB participant : batch) {
                    batchBuilder.addPutItem(participant);
                }

                BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(
                        BatchWriteItemEnhancedRequest.builder()
                                .writeBatches(batchBuilder.build())
                                .build()
                );

                successCount += batch.size();

                if (!result.unprocessedPutItemsForTable(participantTable).isEmpty()) {
                    int unprocessedCount = result.unprocessedPutItemsForTable(participantTable).size();
                    log.warn("Batch {} had {} unprocessed items", i + 1, unprocessedCount);
                    errors.add(ImportError.builder()
                            .rowNumber(null)
                            .errorType(BATCH_WRITE_ERROR)
                            .field(null)
                            .message(String.format("Batch %d: %d items failed to write", i + 1, unprocessedCount))
                            .build());
                    successCount -= unprocessedCount;
                }
            } catch (Exception e) {
                log.error("Failed to write batch {}", i + 1, e);
                errors.add(ImportError.builder()
                        .rowNumber(null)
                        .errorType(BATCH_WRITE_ERROR)
                        .field(null)
                        .message(String.format("Batch %d: Failed to write - %s", i + 1, e.getMessage()))
                        .build());
            }
        }

        return successCount;
    }

    private <T> List<List<T>> partitionList(List<T> list) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += BATCH_SIZE) {
            partitions.add(list.subList(i, Math.min(i + BATCH_SIZE, list.size())));
        }
        return partitions;
    }

    private ParticipantResponse mapParticipantToResponse(ParticipantDDB participant) {
        return ParticipantResponse.builder()
                .eventId(participant.getEventId())
                .bibNumber(participant.getBibNumber())
                .chipNumber(participant.getChipNumber())
                .fullName(participant.getFullName())
                .email(participant.getEmail())
                .phoneNumber(participant.getPhoneNumber())
                .dateOfBirth(participant.getDateOfBirth())
                .age(participant.getAge())
                .gender(participant.getGender())
                .country(participant.getCountry())
                .city(participant.getCity())
                .raceId(participant.getRaceId())
                .raceName(participant.getRaceName())
                .categoryId(participant.getCategoryId())
                .categoryName(participant.getCategoryName())
                .goodies(participant.getGoodies())
                .bibCollectedAt(participant.getBibCollectedAt())
                .createdAt(participant.getCreatedAt())
                .updatedAt(participant.getUpdatedAt())
                .build();
    }
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ImportError createImportError(Integer rowNumber, String errorType, String field, String message) {
        return ImportError.builder()
                .rowNumber(rowNumber)
                .errorType(errorType)
                .field(field)
                .message(message)
                .build();
    }

    private void bulkWriteImportErrors(String importId, List<ImportError> errors) {
        if (errors.isEmpty()) {
            return;
        }

        log.info("Writing {} import errors to DynamoDB for import ID: {}", errors.size(), importId);

        List<ImportErrorDDB> errorDDBs = new ArrayList<>();
        for (int i = 0; i < errors.size(); i++) {
            ImportError error = errors.get(i);
            Integer rowNumber = error.getRowNumber() != null ? error.getRowNumber() : i;

            ImportErrorDDB errorDDB = ImportErrorDDB.create(
                    importId,
                    rowNumber,
                    error.getErrorType(),
                    error.getField(),
                    error.getMessage(),
                    ERROR_TTL_DAYS
            );
            errorDDBs.add(errorDDB);
        }

        List<List<ImportErrorDDB>> batches = partitionListForErrors(errorDDBs);

        for (int i = 0; i < batches.size(); i++) {
            List<ImportErrorDDB> batch = batches.get(i);
            log.info("Writing error batch {} of {} ({} items)", i + 1, batches.size(), batch.size());

            try {
                WriteBatch.Builder<ImportErrorDDB> batchBuilder = WriteBatch.builder(ImportErrorDDB.class)
                        .mappedTableResource(importErrorTable);

                for (ImportErrorDDB errorDDB : batch) {
                    batchBuilder.addPutItem(errorDDB);
                }

                dynamoDbEnhancedClient.batchWriteItem(
                        BatchWriteItemEnhancedRequest.builder()
                                .writeBatches(batchBuilder.build())
                                .build()
                );
            } catch (Exception e) {
                log.error("Failed to write error batch {} for import {}", i + 1, importId, e);
            }
        }

        log.info("Successfully wrote {} import errors for import ID: {}", errors.size(), importId);
    }

    private <T> List<List<T>> partitionListForErrors(List<T> list) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += ERROR_BATCH_SIZE) {
            partitions.add(list.subList(i, Math.min(i + ERROR_BATCH_SIZE, list.size())));
        }
        return partitions;
    }

    private ErrorSummary buildErrorSummary(List<ImportError> errors) {
        int validationErrors = 0;
        int duplicateBibErrors = 0;
        int referenceErrors = 0;
        int batchWriteErrors = 0;
        int processingErrors = 0;

        for (ImportError error : errors) {
            switch (error.getErrorType()) {
                case "VALIDATION_ERROR":
                    validationErrors++;
                    break;
                case "DUPLICATE_BIB":
                    duplicateBibErrors++;
                    break;
                case "REFERENCE_ERROR":
                    referenceErrors++;
                    break;
                case BATCH_WRITE_ERROR:
                    batchWriteErrors++;
                    break;
                case "PROCESSING_ERROR":
                    processingErrors++;
                    break;
                default:
                    break;
            }
        }

        return ErrorSummary.builder()
                .validationErrors(validationErrors)
                .duplicateBibErrors(duplicateBibErrors)
                .referenceErrors(referenceErrors)
                .batchWriteErrors(batchWriteErrors)
                .processingErrors(processingErrors)
                .build();
    }

    private String serializeErrorSummary(ErrorSummary errorSummary) {
        try {
            return objectMapper.writeValueAsString(errorSummary);
        } catch (Exception e) {
            log.error("Failed to serialize error summary", e);
            return "{}";
        }
    }

    private int executeBatchDelete(List<ParticipantDDB> participants) {
        if (participants.isEmpty()) {
            return 0;
        }

        WriteBatch.Builder<ParticipantDDB> batchBuilder = WriteBatch.builder(ParticipantDDB.class)
                .mappedTableResource(participantTable);

        for (ParticipantDDB participant : participants) {
            batchBuilder.addDeleteItem(Key.builder()
                    .partitionValue(participant.getEventId())
                    .sortValue(participant.getBibNumber())
                    .build());
        }

        BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(
                BatchWriteItemEnhancedRequest.builder()
                        .writeBatches(batchBuilder.build())
                        .build()
        );

        int deletedCount = participants.size();
        if (!result.unprocessedDeleteItemsForTable(participantTable).isEmpty()) {
            int unprocessedCount = result.unprocessedDeleteItemsForTable(participantTable).size();
            deletedCount -= unprocessedCount;
        }

        return deletedCount;
    }

    private int deleteAllParticipantsForEvent(Long eventId) {
        log.info("Querying all participants for event ID: {} to delete", eventId);

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(eventId.toString()).build()
        );

        List<ParticipantDDB> allParticipants = participantTable.query(queryConditional).stream()
                .flatMap(page -> page.items().stream())
                .toList();

        if (allParticipants.isEmpty()) {
            log.info("No existing participants found for event ID: {}", eventId);
            return 0;
        }

        log.info("Found {} existing participants to delete for event ID: {}", allParticipants.size(), eventId);

        List<List<ParticipantDDB>> batches = partitionList(allParticipants);
        int totalDeletedCount = 0;

        for (int i = 0; i < batches.size(); i++) {
            List<ParticipantDDB> batch = batches.get(i);
            log.info("Deleting batch {} of {} ({} items)", i + 1, batches.size(), batch.size());

            try {
                int deletedCount = executeBatchDelete(batch);
                totalDeletedCount += deletedCount;

                int unprocessedCount = batch.size() - deletedCount;
                if (unprocessedCount > 0) {
                    log.warn("Delete batch {} had {} unprocessed items", i + 1, unprocessedCount);
                }
            } catch (Exception e) {
                log.error("Failed to delete batch {} for event {}", i + 1, eventId, e);
            }
        }

        log.info("Successfully deleted {} participants for event ID: {}", totalDeletedCount, eventId);
        return totalDeletedCount;
    }

    @Override
    public ImportJobListResponse getImportJobsByEvent(Long eventId, Integer page, Integer size, User currentUser) {
        log.info("Fetching import jobs for event ID: {} (page: {}, size: {}) by user: {}",
                eventId, page, size, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);

        int pageNumber = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        org.springframework.data.domain.Page<ImportJob> importJobPage =
                importJobRepository.findByEventIdOrderByImportedAtDesc(
                        eventId,
                        org.springframework.data.domain.PageRequest.of(pageNumber, pageSize)
                );

        List<ImportJobResponse> importJobResponses = importJobPage.getContent().stream()
                .map(this::mapImportJobToResponse)
                .toList();

        return ImportJobListResponse.builder()
                .imports(importJobResponses)
                .totalCount((int) importJobPage.getTotalElements())
                .currentPage(pageNumber)
                .pageSize(pageSize)
                .totalPages(importJobPage.getTotalPages())
                .build();
    }

    @Override
    public ImportJobResponse getImportJobDetails(Long eventId, String importId, User currentUser) {
        log.info("Fetching import job details for import ID: {} and event ID: {} by user: {}",
                importId, eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);

        ImportJob importJob = importJobRepository.findByImportIdAndEventId(importId, eventId)
                .orElseThrow(() -> new InvalidUserDataException("Import job not found with ID: " + importId));

        return mapImportJobToResponse(importJob);
    }

    @Override
    public ImportErrorListResponse getImportErrors(Long eventId, String importId, Integer limit, String lastEvaluatedKey, User currentUser) {
        log.info("Fetching import errors for import ID: {} (limit: {}) by user: {}",
                importId, limit, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);

        ImportJob importJob = importJobRepository.findByImportIdAndEventId(importId, eventId)
                .orElseThrow(() -> new InvalidUserDataException("Import job not found with ID: " + importId));

        int effectiveLimit = limit != null ? Math.min(limit, 100) : 50;

        ErrorSummary errorSummary = deserializeErrorSummary(importJob.getErrorSummary());

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(importId).build()
        );

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(effectiveLimit);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            try {
                Map<String, AttributeValue> exclusiveStartKey = paginationCodec.decode(lastEvaluatedKey);
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            } catch (Exception e) {
                log.error(FAILED_TO_DECODE_PAGINATION_KEY, e);
                throw new InvalidUserDataException("Invalid pagination key");
            }
        }

        Page<ImportErrorDDB> page = importErrorTable.query(requestBuilder.build())
                .stream()
                .findFirst()
                .orElse(null);

        if (page == null) {
            return ImportErrorListResponse.builder()
                    .importId(importId)
                    .errors(Collections.emptyList())
                    .count(0)
                    .hasMore(false)
                    .errorSummary(errorSummary)
                    .build();
        }

        List<ImportError> errors = page.items().stream()
                .map(this::mapImportErrorDDBToDTO)
                .toList();

        String newLastEvaluatedKey = null;
        if (page.lastEvaluatedKey() != null && !page.lastEvaluatedKey().isEmpty()) {
            newLastEvaluatedKey = paginationCodec.encode(page.lastEvaluatedKey());
        }

        log.info("Fetched {} errors for import ID: {}, hasMore: {}",
                errors.size(), importId, newLastEvaluatedKey != null);

        return ImportErrorListResponse.builder()
                .importId(importId)
                .errors(errors)
                .count(errors.size())
                .lastEvaluatedKey(newLastEvaluatedKey)
                .hasMore(newLastEvaluatedKey != null)
                .errorSummary(errorSummary)
                .build();
    }

    private ImportJobResponse mapImportJobToResponse(ImportJob importJob) {
        ErrorSummary errorSummary = deserializeErrorSummary(importJob.getErrorSummary());

        return ImportJobResponse.builder()
                .importId(importJob.getImportId())
                .eventId(importJob.getEventId())
                .eventName(importJob.getEventName())
                .fileName(importJob.getFileName())
                .totalRows(importJob.getTotalRows())
                .successCount(importJob.getSuccessCount())
                .failureCount(importJob.getFailureCount())
                .errorSummary(errorSummary)
                .status(importJob.getStatus().name())
                .importedBy(importJob.getImportedBy())
                .importedAt(importJob.getImportedAt())
                .build();
    }

    private ImportError mapImportErrorDDBToDTO(ImportErrorDDB errorDDB) {
        return ImportError.builder()
                .rowNumber(errorDDB.getRowNumber())
                .errorType(errorDDB.getErrorType())
                .field(errorDDB.getField())
                .message(errorDDB.getMessage())
                .build();
    }

    private ErrorSummary deserializeErrorSummary(String errorSummaryJson) {
        try {
            if (errorSummaryJson == null || errorSummaryJson.trim().isEmpty() || errorSummaryJson.equals("{}")) {
                return ErrorSummary.builder().build();
            }
            return objectMapper.readValue(errorSummaryJson, ErrorSummary.class);
        } catch (Exception e) {
            log.error("Failed to deserialize error summary", e);
            return ErrorSummary.builder().build();
        }
    }

    @Override
    public DeleteParticipantsResponse deleteParticipant(Long eventId, String bibNumber, User currentUser) {
        log.info("Deleting participant with bib {} for event ID: {} by user: {}", bibNumber, eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);

        ParticipantDDB participant = participantTable.getItem(
                Key.builder()
                        .partitionValue(eventId.toString())
                        .sortValue(bibNumber)
                        .build()
        );

        if (participant == null) {
            throw new ParticipantNotFoundException("Participant not found with bib number: " + bibNumber);
        }

        participantTable.deleteItem(
                Key.builder()
                        .partitionValue(eventId.toString())
                        .sortValue(bibNumber)
                        .build()
        );

        String message = String.format("Successfully deleted participant with bib %s from event '%s'", bibNumber, event.getEventName());
        log.info(message);

        return DeleteParticipantsResponse.builder()
                .eventId(eventId)
                .eventName(event.getEventName())
                .deletedCount(1)
                .failedCount(0)
                .message(message)
                .build();
    }

    @Override
    public DeleteParticipantsResponse deleteAllParticipants(Long eventId, User currentUser) {
        log.info("Deleting all participants for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        int deletedCount = deleteAllParticipantsForEvent(eventId);

        String message = String.format("Successfully deleted %d participants for event '%s'", deletedCount, event.getEventName());
        log.info(message);

        return DeleteParticipantsResponse.builder()
                .eventId(eventId)
                .eventName(event.getEventName())
                .deletedCount(deletedCount)
                .failedCount(0)
                .message(message)
                .build();
    }

    @Override
    public DeleteParticipantsResponse deleteBulkParticipants(Long eventId, List<String> bibNumbers, User currentUser) {
        log.info("Deleting {} participants for event ID: {} by user: {}", bibNumbers.size(), eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);

        // TODO: why only 25 max delete option
        if (bibNumbers.size() > 25) {
            throw new InvalidUserDataException("Cannot delete more than 25 participants at once");
        }

        int deletedCount = 0;
        int failedCount = 0;
        List<String> notFoundBibs = new ArrayList<>();

        List<ParticipantDDB> participantsToDelete = new ArrayList<>();

        for (String bibNumber : bibNumbers) {
            ParticipantDDB participant = participantTable.getItem(
                    Key.builder()
                            .partitionValue(eventId.toString())
                            .sortValue(bibNumber)
                            .build()
            );

            if (participant == null) {
                notFoundBibs.add(bibNumber);
                failedCount++;
            } else {
                participantsToDelete.add(participant);
            }
        }

        if (!participantsToDelete.isEmpty()) {
            try {
                deletedCount = executeBatchDelete(participantsToDelete);

                int unprocessedCount = participantsToDelete.size() - deletedCount;
                if (unprocessedCount > 0) {
                    log.warn("Bulk delete had {} unprocessed items", unprocessedCount);
                    failedCount += unprocessedCount;
                }
            } catch (Exception e) {
                log.error("Failed to delete participants in bulk for event {}", eventId, e);
                throw new CsvImportException("Failed to delete participants: " + e.getMessage(), e);
            }
        }

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(String.format("Successfully deleted %d participants for event '%s'", deletedCount, event.getEventName()));

        if (failedCount > 0) {
            messageBuilder.append(String.format(", %d failed", failedCount));
            if (!notFoundBibs.isEmpty()) {
                messageBuilder.append(String.format(" (Not found: %s)", String.join(", ", notFoundBibs)));
            }
        }

        String message = messageBuilder.toString();
        log.info(message);

        return DeleteParticipantsResponse.builder()
                .eventId(eventId)
                .eventName(event.getEventName())
                .deletedCount(deletedCount)
                .failedCount(failedCount)
                .message(message)
                .build();
    }

    private void updateRaceAndCategory(ParticipantDDB participant, UpdateParticipantRequest request,
                                       Long eventId, User currentUser) {

        String newRaceId = request.getRaceId() != null ? request.getRaceId() : participant.getRaceId();
        String newCategoryId = request.getCategoryId() != null ? request.getCategoryId() : participant.getCategoryId();

        RaceResponse race = raceService.getRaceById(eventId, Long.parseLong(newRaceId), currentUser);

        CategoryResponse category = categoryService.getCategoryById(eventId, Long.parseLong(newRaceId),
                Long.parseLong(newCategoryId), currentUser);

        participant.setRaceId(newRaceId);
        participant.setRaceName(race.getRaceName());
        participant.setCategoryId(newCategoryId);
        participant.setCategoryName(category.getCategoryName());
    }

    private <T> void updateIfNotNull(T value, java.util.function.Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    @Override
    public ParticipantListResponse searchParticipants(
            Long eventId,
            String searchTerm,
            String raceId,
            String categoryId,
            String gender,
            Integer minAge,
            Integer maxAge,
            String city,
            String country,
            Integer limit,
            String lastEvaluatedKey,
            User currentUser) {

        log.info("Searching participants for event ID: {} with searchTerm: '{}', filters: [raceId={}, categoryId={}, gender={}, minAge={}, maxAge={}, city={}, country={}] by user: {}",
                eventId, searchTerm, raceId, categoryId, gender, minAge, maxAge, city, country, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        validateSearchRequest(searchTerm, minAge, maxAge, limit);

        Expression filterExpression = buildSearchFilterExpression(
                eventId, searchTerm, raceId, categoryId, gender, minAge, maxAge, city, country);

        ScanEnhancedRequest.Builder scanBuilder = ScanEnhancedRequest.builder()
                .filterExpression(filterExpression)
                .limit(limit != null ? Math.min(limit, 100) : 50);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            try {
                Map<String, AttributeValue> exclusiveStartKey = paginationCodec.decode(lastEvaluatedKey);
                scanBuilder.exclusiveStartKey(exclusiveStartKey);
            } catch (Exception e) {
                log.error(FAILED_TO_DECODE_PAGINATION_KEY, e);
                throw new InvalidUserDataException("Invalid pagination key");
            }
        }

        Page<ParticipantDDB> page = participantTable.scan(scanBuilder.build())
                .stream()
                .findFirst()
                .orElse(null);

        if (page == null) {
            return ParticipantListResponse.builder()
                    .participants(Collections.emptyList())
                    .count(0)
                    .hasMore(false)
                    .build();
        }

        List<ParticipantResponse> participants = page.items().stream()
                .map(this::mapParticipantToResponse)
                .toList();

        String newLastEvaluatedKey = null;
        if (page.lastEvaluatedKey() != null && !page.lastEvaluatedKey().isEmpty()) {
            newLastEvaluatedKey = paginationCodec.encode(page.lastEvaluatedKey());
        }

        log.info("Search completed for event ID: {}. Found {} participants, hasMore: {}",
                eventId, participants.size(), newLastEvaluatedKey != null);

        return ParticipantListResponse.builder()
                .participants(participants)
                .lastEvaluatedKey(newLastEvaluatedKey)
                .count(participants.size())
                .hasMore(newLastEvaluatedKey != null)
                .build();
    }

    private void validateSearchRequest(String searchTerm, Integer minAge, Integer maxAge, Integer limit) {
        if (searchTerm != null) {
            String trimmedSearchTerm = searchTerm.trim();
            if (trimmedSearchTerm.length() == 1) {
                throw new InvalidUserDataException("Search term must be at least 2 characters");
            }

            if (searchTerm.length() > 200) {
                throw new InvalidUserDataException("Search term cannot exceed 200 characters");
            }
        }

        if (minAge != null && (minAge < 0 || minAge > 150)) {
            throw new InvalidUserDataException("Minimum age must be between 0 and 150");
        }

        if (maxAge != null && (maxAge < 0 || maxAge > 150)) {
            throw new InvalidUserDataException("Maximum age must be between 0 and 150");
        }

        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw new InvalidUserDataException("Minimum age cannot be greater than maximum age");
        }

        if (limit != null && limit > 100) {
            throw new InvalidUserDataException("Limit cannot exceed 100");
        }
    }

    private Expression buildSearchFilterExpression(
            Long eventId,
            String searchTerm,
            String raceId,
            String categoryId,
            String gender,
            Integer minAge,
            Integer maxAge,
            String city,
            String country) {

        List<String> conditions = new ArrayList<>();
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        Map<String, String> expressionNames = new HashMap<>();

        conditions.add("#eventId = :eventId");
        expressionNames.put("#eventId", "eventId");
        expressionValues.put(":eventId", AttributeValue.builder()
                .s(eventId.toString()).build());

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String upperSearchTerm = searchTerm.trim().toUpperCase();
            String lowerSearchTerm = searchTerm.trim().toLowerCase();
            String exactSearchTerm = searchTerm.trim();

            List<String> searchConditions = new ArrayList<>();

            searchConditions.add("contains(#fullName, :searchTermUpper)");
            expressionNames.put("#fullName", "fullName");
            expressionValues.put(":searchTermUpper",
                    AttributeValue.builder().s(upperSearchTerm).build());

            searchConditions.add("contains(#email, :searchTermLower)");
            expressionNames.put("#email", "email");
            expressionValues.put(":searchTermLower",
                    AttributeValue.builder().s(lowerSearchTerm).build());

            searchConditions.add("contains(#phoneNumber, :searchTermExact)");
            expressionNames.put("#phoneNumber", "phoneNumber");
            expressionValues.put(":searchTermExact",
                    AttributeValue.builder().s(exactSearchTerm).build());

            searchConditions.add("contains(#chipNumber, :searchTermExact)");
            expressionNames.put("#chipNumber", "chipNumber");



            conditions.add("(" + String.join(" OR ", searchConditions) + ")");
        }

        if (raceId != null && !raceId.trim().isEmpty()) {
            conditions.add("#raceId = :raceId");
            expressionNames.put("#raceId", "raceId");
            expressionValues.put(":raceId",
                    AttributeValue.builder().s(raceId).build());
        }

        if (categoryId != null && !categoryId.trim().isEmpty()) {
            conditions.add("#categoryId = :categoryId");
            expressionNames.put("#categoryId", "categoryId");
            expressionValues.put(":categoryId",
                    AttributeValue.builder().s(categoryId).build());
        }

        if (gender != null && !gender.trim().isEmpty()) {
            conditions.add("#gender = :gender");
            expressionNames.put("#gender", "gender");
            expressionValues.put(":gender",
                    AttributeValue.builder().s(gender.toUpperCase()).build());
        }

        if (minAge != null) {
            conditions.add("#age >= :minAge");
            expressionNames.put("#age", "age");
            expressionValues.put(":minAge",
                    AttributeValue.builder().n(minAge.toString()).build());
        }

        if (maxAge != null) {
            conditions.add("#age <= :maxAge");
            expressionNames.putIfAbsent("#age", "age");
            expressionValues.put(":maxAge",
                    AttributeValue.builder().n(maxAge.toString()).build());
        }

        if (city != null && !city.trim().isEmpty()) {
            conditions.add("contains(#city, :city)");
            expressionNames.put("#city", "city");
            expressionValues.put(":city",
                    AttributeValue.builder().s(city.trim().toUpperCase()).build());
        }

        if (country != null && !country.trim().isEmpty()) {
            conditions.add("contains(#country, :country)");
            expressionNames.put("#country", "country");
            expressionValues.put(":country",
                    AttributeValue.builder().s(country.trim().toUpperCase()).build());
        }

        String filterExpression = String.join(" AND ", conditions);

        return Expression.builder()
                .expression(filterExpression)
                .expressionValues(expressionValues)
                .expressionNames(expressionNames)
                .build();
    }

    @Override
    public ParticipantListResponse lookupParticipants(
            Long eventId,
            SearchType searchType,
            String searchValue,
            Integer limit,
            String lastEvaluatedKey,
            User currentUser) {

        log.info("Lookup participants for event ID: {} with searchType: {}, searchValue: '{}' by user: {}",
                eventId, searchType, searchValue, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        if (searchValue == null || searchValue.trim().isEmpty()) {
            throw new InvalidUserDataException("Search value is required");
        }

        int effectiveLimit = limit != null ? Math.min(limit, 100) : 50;

        if (searchType == SearchType.BIB) {
            return lookupByBibNumber(eventId, searchValue.trim());
        }

        String indexName = getIndexNameForSearchType(searchType);
        String normalizedSearchValue = normalizeSearchValue(searchType, searchValue.trim());

        DynamoDbIndex<ParticipantDDB> index = participantTable.index(indexName);

        QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(eventId.toString())
                        .sortValue(normalizedSearchValue)
                        .build()
        );

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(effectiveLimit);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            try {
                Map<String, AttributeValue> exclusiveStartKey = paginationCodec.decode(lastEvaluatedKey);
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            } catch (Exception e) {
                log.error(FAILED_TO_DECODE_PAGINATION_KEY, e);
                throw new InvalidUserDataException("Invalid pagination key");
            }
        }

        Page<ParticipantDDB> page = index.query(requestBuilder.build())
                .stream()
                .findFirst()
                .orElse(null);

        if (page == null) {
            return ParticipantListResponse.builder()
                    .participants(Collections.emptyList())
                    .count(0)
                    .hasMore(false)
                    .build();
        }

        List<ParticipantResponse> participants = page.items().stream()
                .map(this::mapParticipantToResponse)
                .toList();

        String newLastEvaluatedKey = null;
        if (page.lastEvaluatedKey() != null && !page.lastEvaluatedKey().isEmpty()) {
            newLastEvaluatedKey = paginationCodec.encode(page.lastEvaluatedKey());
        }

        log.info("Lookup completed for event ID: {} using index {}. Found {} participants, hasMore: {}",
                eventId, indexName, participants.size(), newLastEvaluatedKey != null);

        return ParticipantListResponse.builder()
                .participants(participants)
                .lastEvaluatedKey(newLastEvaluatedKey)
                .count(participants.size())
                .hasMore(newLastEvaluatedKey != null)
                .build();
    }

    private ParticipantListResponse lookupByBibNumber(Long eventId, String bibNumber) {
        ParticipantDDB participant = participantTable.getItem(
                Key.builder()
                        .partitionValue(eventId.toString())
                        .sortValue(bibNumber)
                        .build()
        );

        if (participant == null) {
            return ParticipantListResponse.builder()
                    .participants(Collections.emptyList())
                    .count(0)
                    .hasMore(false)
                    .build();
        }

        return ParticipantListResponse.builder()
                .participants(List.of(mapParticipantToResponse(participant)))
                .count(1)
                .hasMore(false)
                .build();
    }

    private String getIndexNameForSearchType(SearchType searchType) {
        return switch (searchType) {
            case NAME -> "LSI-FullNameIndex";
            case EMAIL -> "LSI-EmailIndex";
            case PHONE -> "LSI-PhoneNumberIndex";
            case RACE -> "LSI-RaceNameIndex";
            case CATEGORY -> "LSI-CategoryNameIndex";
            case BIB -> throw new IllegalArgumentException("BIB search does not use an index");
        };
    }

    private String normalizeSearchValue(SearchType searchType, String searchValue) {
        return switch (searchType) {
            case NAME -> searchValue.toUpperCase();
            case EMAIL -> searchValue.toLowerCase();
            case PHONE, BIB -> searchValue;
            case RACE, CATEGORY -> searchValue;
        };
    }

    @Override
    public byte[] exportParticipantsToCsv(Long eventId, List<ExportField> fields, User currentUser) {
        log.info("Exporting participants to CSV for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(eventId.toString()).build()
        );

        List<ParticipantDDB> allParticipants = participantTable.query(queryConditional).stream()
                .flatMap(page -> page.items().stream())
                .toList();

        log.info("Found {} participants to export for event ID: {}", allParticipants.size(), eventId);

        List<ExportField> exportFields = (fields == null || fields.isEmpty())
                ? Arrays.asList(ExportField.values())
                : fields;

        Set<String> goodiesKeys = collectGoodiesKeys(allParticipants);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            List<String> headers = buildCsvHeaders(exportFields, goodiesKeys);
            csvPrinter.printRecord(headers);

            for (ParticipantDDB participant : allParticipants) {
                List<String> row = buildCsvRow(participant, exportFields, goodiesKeys);
                csvPrinter.printRecord(row);
            }

            csvPrinter.flush();
            log.info("Successfully exported {} participants to CSV for event ID: {}", allParticipants.size(), eventId);
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Failed to generate CSV for event ID: {}", eventId, e);
            throw new CsvImportException("Failed to generate CSV export", e);
        }
    }

    private Set<String> collectGoodiesKeys(List<ParticipantDDB> participants) {
        Set<String> keys = new LinkedHashSet<>();
        for (ParticipantDDB participant : participants) {
            if (participant.getGoodies() != null) {
                keys.addAll(participant.getGoodies().keySet());
            }
        }
        return keys;
    }

    private List<String> buildCsvHeaders(List<ExportField> fields, Set<String> goodiesKeys) {
        List<String> headers = new ArrayList<>();

        for (ExportField field : fields) {
            if (field == ExportField.GOODIES) {
                headers.addAll(goodiesKeys);
            } else {
                headers.add(getHeaderName(field));
            }
        }

        return headers;
    }

    private String getHeaderName(ExportField field) {
        return switch (field) {
            case CHIP_NUMBER -> "CHIP No";
            case BIB_NUMBER -> "BIB No";
            case FULL_NAME -> "NAME";
            case DATE_OF_BIRTH -> "DOB(dd-mm-yyy)";
            case AGE -> "Age";
            case GENDER -> "Gender";
            case RACE_NAME -> "Race";
            case CATEGORY_NAME -> "Category";
            case PHONE_NUMBER -> "Phone";
            case EMAIL -> "Email-Id";
            case COUNTRY -> "Country";
            case CITY -> "City";
            case BIB_COLLECTED_AT -> "Bib Collected At";
            case EMERGENCY_CONTACT_NAME -> "Emergency Contact Name";
            case EMERGENCY_CONTACT_PHONE -> "Emergency Contact Phone";
            case NOTES -> "Notes";
            case GOODIES -> "Goodies";
        };
    }

    private List<String> buildCsvRow(ParticipantDDB participant, List<ExportField> fields, Set<String> goodiesKeys) {
        List<String> row = new ArrayList<>();

        for (ExportField field : fields) {
            if (field == ExportField.GOODIES) {
                for (String goodieKey : goodiesKeys) {
                    String value = participant.getGoodies() != null
                            ? participant.getGoodies().getOrDefault(goodieKey, "")
                            : "";
                    row.add(value);
                }
            } else {
                row.add(getFieldValue(participant, field));
            }
        }

        return row;
    }

    private String getFieldValue(ParticipantDDB participant, ExportField field) {
        return switch (field) {
            case BIB_NUMBER -> nullSafe(participant.getBibNumber());
            case CHIP_NUMBER -> nullSafe(participant.getChipNumber());
            case FULL_NAME -> nullSafe(participant.getFullName());
            case EMAIL -> nullSafe(participant.getEmail());
            case PHONE_NUMBER -> nullSafe(participant.getPhoneNumber());
            case DATE_OF_BIRTH -> nullSafe(participant.getDateOfBirth());
            case AGE -> participant.getAge() != null ? participant.getAge().toString() : "";
            case GENDER -> nullSafe(participant.getGender());
            case COUNTRY -> nullSafe(participant.getCountry());
            case CITY -> nullSafe(participant.getCity());
            case RACE_NAME -> nullSafe(participant.getRaceName());
            case CATEGORY_NAME -> nullSafe(participant.getCategoryName());
            case BIB_COLLECTED_AT -> nullSafe(participant.getBibCollectedAt());
            case EMERGENCY_CONTACT_NAME -> nullSafe(participant.getEmergencyContactName());
            case EMERGENCY_CONTACT_PHONE -> nullSafe(participant.getEmergencyContactPhone());
            case NOTES -> nullSafe(participant.getNotes());
            case GOODIES -> "";
        };
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    // TODO: Implement statistics aggregation - total count, bib collection status, breakdown by race/category/gender
    @Override
    public ParticipantStatisticsResponse getParticipantStatistics(Long eventId, User currentUser) {
        log.info("Getting participant statistics for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        return ParticipantStatisticsResponse.builder()
                .eventId(eventId)
                .status("COMING_SOON")
                .build();
    }
}
