package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.*;
import com.timekeeper.bibexpo.model.dto.request.CreateParticipantRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateParticipantRequest;
import com.timekeeper.bibexpo.model.dto.response.CategoryResponse;
import com.timekeeper.bibexpo.model.dto.response.DeleteParticipantsResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantListResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.RaceResponse;
import com.timekeeper.bibexpo.model.dynamodb.EventStatsDDB;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.*;
import com.timekeeper.bibexpo.model.enums.ExportField;
import com.timekeeper.bibexpo.model.enums.SearchType;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.dynamodb.EventStatsDDBRepository;
import com.timekeeper.bibexpo.service.CategoryService;
import com.timekeeper.bibexpo.service.EventService;
import com.timekeeper.bibexpo.service.EventStatsService;
import com.timekeeper.bibexpo.service.ParticipantService;
import com.timekeeper.bibexpo.service.RaceService;
import com.timekeeper.bibexpo.service.util.DynamoDBPaginationCodec;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantServiceImpl implements ParticipantService {

    public static final String FAILED_TO_DECODE_PAGINATION_KEY = "Failed to decode pagination key";
    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final EventService eventService;
    private final EventRepository eventRepository;
    private final RaceService raceService;
    private final CategoryService categoryService;
    private final EventAccessValidator validator;
    private final DynamoDBPaginationCodec paginationCodec;
    private final EventStatsDDBRepository eventStatsRepo;
    private final EventStatsService eventStatsService;

    private DynamoDbTable<ParticipantDDB> participantTable;

    private static final int BATCH_SIZE = 25;


    @PostConstruct
    public void init() {
        this.participantTable = dynamoDbEnhancedClient.table(
                "marathon-participants",
                TableSchema.fromBean(ParticipantDDB.class)
        );
    }

    @Override
    public ParticipantResponse createParticipant(Long eventId, CreateParticipantRequest request, User currentUser) {
        log.info("Creating participant with BIB {} for event ID: {} by user: {}",
                request.getBibNumber(), eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        ParticipantDDB existingParticipant = participantTable.getItem(
                Key.builder()
                        .partitionValue(eventId.toString())
                        .sortValue(request.getBibNumber())
                        .build()
        );

        if (existingParticipant != null) {
            throw new IllegalArgumentException("A participant with this BIB number already exists.");
        }

        RaceResponse race = raceService.getRaceById(eventId, request.getRaceId(), currentUser);
        CategoryResponse category = categoryService.getCategoryById(eventId, request.getRaceId(),
                request.getCategoryId(), currentUser);

        String timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();

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
        eventStatsService.onParticipantCreated(participant);

        log.info("Successfully created participant with BIB {} for event ID: {}", request.getBibNumber(), eventId);

        return mapParticipantToResponse(participant);
    }

    @Override
    public ParticipantListResponse getParticipantsByEvent(Long eventId, Integer limit, String lastEvaluatedKey, User currentUser) {
        log.info("Fetching participants for event ID: {} with limit: {} by user: {}",
                eventId, limit, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

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
                throw new InvalidUserDataException("Invalid page token. Please start from the first page.");
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
                .orElseThrow(EventNotFoundException::new);

        validator.validateUserAuthorizationForEvent(currentUser, event);

        ParticipantDDB participant = participantTable.getItem(
                Key.builder()
                        .partitionValue(eventId.toString())
                        .sortValue(bibNumber)
                        .build()
        );

        if (participant == null) {
            throw new ParticipantNotFoundException();
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
                .orElseThrow(EventNotFoundException::new);

        validator.validateUserAuthorizationForEvent(currentUser, event);

        ParticipantDDB participant = participantTable.getItem(
                Key.builder()
                        .partitionValue(eventId.toString())
                        .sortValue(bibNumber)
                        .build()
        );

        if (participant == null) {
            throw new ParticipantNotFoundException();
        }

        ParticipantDDB beforeSnapshot = snapshotForStats(participant);

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
                throw new IllegalArgumentException("A participant with this BIB number already exists.");
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

        String timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
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

        eventStatsService.onParticipantUpdated(beforeSnapshot, participant);

        return mapParticipantToResponse(participant);
    }

    @Override
    public Long getParticipantCount(Long eventId, User currentUser) {
        log.info("Counting participants for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

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
                .bibCollectedByName(participant.getBibCollectedByName())
                .bibCollectedByPhone(participant.getBibCollectedByPhone())
                .bibDistributedBy(participant.getBibDistributedBy())
                .goodiesDistribution(participant.getGoodiesDistribution())
                .emergencyContactName(participant.getEmergencyContactName())
                .emergencyContactPhone(participant.getEmergencyContactPhone())
                .notes(participant.getNotes())
                .createdAt(participant.getCreatedAt())
                .createdBy(participant.getCreatedBy())
                .updatedAt(participant.getUpdatedAt())
                .updatedBy(participant.getUpdatedBy())
                .build();
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
    public DeleteParticipantsResponse deleteParticipant(Long eventId, String bibNumber, User currentUser) {
        log.info("Deleting participant with bib {} for event ID: {} by user: {}", bibNumber, eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        validator.validateUserAuthorizationForEvent(currentUser, event);

        ParticipantDDB participant = participantTable.getItem(
                Key.builder()
                        .partitionValue(eventId.toString())
                        .sortValue(bibNumber)
                        .build()
        );

        if (participant == null) {
            throw new ParticipantNotFoundException();
        }

        participantTable.deleteItem(
                Key.builder()
                        .partitionValue(eventId.toString())
                        .sortValue(bibNumber)
                        .build()
        );
        eventStatsService.onParticipantDeleted(participant);

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
                .orElseThrow(EventNotFoundException::new);

        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        int deletedCount = deleteAllParticipantsForEvent(eventId);
        eventStatsRepo.deleteAllByEventId(eventId.toString());

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
                .orElseThrow(EventNotFoundException::new);

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
                eventStatsService.onBulkDeleted(participantsToDelete);
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

    private <T> void updateIfNotNull(T value, Consumer<T> setter) {
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
                .orElseThrow(EventNotFoundException::new);

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
                throw new InvalidUserDataException("Invalid page token. Please start from the first page.");
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
                .orElseThrow(EventNotFoundException::new);

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
                throw new InvalidUserDataException("Invalid page token. Please start from the first page.");
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
                .orElseThrow(() -> new EventNotFoundException());

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

    @Override
    public ParticipantStatisticsResponse getParticipantStatistics(Long eventId, User currentUser) {
        log.info("Getting participant statistics for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException());

        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        List<EventStatsDDB> rows = eventStatsRepo.queryAll(eventId.toString());
        if (rows.isEmpty()) {
            log.warn("No stats counters found for event {} — call POST /participants/statistics/reconcile to backfill",
                    eventId);
            return emptyStatistics(eventId);
        }

        return buildStatisticsFromRows(eventId, rows);
    }

    private ParticipantStatisticsResponse buildStatisticsFromRows(Long eventId, List<EventStatsDDB> rows) {
        int total = 0;
        int bibCollected = 0;
        int male = 0;
        int female = 0;
        int other = 0;
        Map<String, ParticipantStatisticsResponse.RaceStatistics> raceMap = new LinkedHashMap<>();
        Map<String, ParticipantStatisticsResponse.CategoryStatistics> categoryMap = new LinkedHashMap<>();

        for (EventStatsDDB row : rows) {
            String key = row.getStatKey();
            long count = row.getCount() != null ? row.getCount() : 0L;
            if (count < 0) continue;

            switch (key) {
                case EventStatsServiceImpl.KEY_TOTAL -> total = (int) count;
                case EventStatsServiceImpl.KEY_BIB_COLLECTED -> bibCollected = (int) count;
                case EventStatsServiceImpl.GENDER_M -> male = (int) count;
                case EventStatsServiceImpl.GENDER_F -> female = (int) count;
                case EventStatsServiceImpl.GENDER_O -> other = (int) count;
                default -> applyDimensionRow(key, count, row, raceMap, categoryMap);
            }
        }

        log.info("Loaded statistics for event {} from counters: total={} bibCollected={}",
                eventId, total, bibCollected);

        return ParticipantStatisticsResponse.builder()
                .eventId(eventId)
                .totalParticipants(total)
                .bibCollectedCount(bibCollected)
                .pendingCount(Math.max(0, total - bibCollected))
                .raceBreakdown(new ArrayList<>(raceMap.values()))
                .categoryBreakdown(new ArrayList<>(categoryMap.values()))
                .genderBreakdown(ParticipantStatisticsResponse.GenderStatistics.builder()
                        .male(male)
                        .female(female)
                        .other(other)
                        .build())
                .build();
    }

    private void applyDimensionRow(
            String key, long count, EventStatsDDB row,
            Map<String, ParticipantStatisticsResponse.RaceStatistics> raceMap,
            Map<String, ParticipantStatisticsResponse.CategoryStatistics> categoryMap) {

        if (key.startsWith(EventStatsServiceImpl.PREFIX_RACE)) {
            applyRaceRow(key, count, row, raceMap);
        } else if (key.startsWith(EventStatsServiceImpl.PREFIX_CATEGORY)) {
            applyCategoryRow(key, count, row, categoryMap);
        }
    }

    private void applyRaceRow(
            String key, long count, EventStatsDDB row,
            Map<String, ParticipantStatisticsResponse.RaceStatistics> raceMap) {

        boolean collected = key.endsWith(EventStatsServiceImpl.SUFFIX_COLLECTED);
        String raceId = collected
                ? key.substring(EventStatsServiceImpl.PREFIX_RACE.length(),
                        key.length() - EventStatsServiceImpl.SUFFIX_COLLECTED.length())
                : key.substring(EventStatsServiceImpl.PREFIX_RACE.length());

        ParticipantStatisticsResponse.RaceStatistics rs = raceMap.computeIfAbsent(raceId,
                k -> ParticipantStatisticsResponse.RaceStatistics.builder()
                        .raceId(k)
                        .raceName(row.getRaceName())
                        .count(0)
                        .bibCollectedCount(0)
                        .build());

        if (rs.getRaceName() == null && row.getRaceName() != null) {
            rs.setRaceName(row.getRaceName());
        }
        if (collected) {
            rs.setBibCollectedCount((int) count);
        } else {
            rs.setCount((int) count);
        }
    }

    private void applyCategoryRow(
            String key, long count, EventStatsDDB row,
            Map<String, ParticipantStatisticsResponse.CategoryStatistics> categoryMap) {

        if (key.endsWith(EventStatsServiceImpl.SUFFIX_COLLECTED)) return;

        String categoryId = key.substring(EventStatsServiceImpl.PREFIX_CATEGORY.length());
        ParticipantStatisticsResponse.CategoryStatistics cs = categoryMap.computeIfAbsent(categoryId,
                k -> ParticipantStatisticsResponse.CategoryStatistics.builder()
                        .categoryId(k)
                        .categoryName(row.getCategoryName())
                        .count(0)
                        .build());

        if (cs.getCategoryName() == null && row.getCategoryName() != null) {
            cs.setCategoryName(row.getCategoryName());
        }
        cs.setCount((int) count);
    }

    private ParticipantDDB snapshotForStats(ParticipantDDB p) {
        return ParticipantDDB.builder()
                .eventId(p.getEventId())
                .bibNumber(p.getBibNumber())
                .raceId(p.getRaceId())
                .raceName(p.getRaceName())
                .categoryId(p.getCategoryId())
                .categoryName(p.getCategoryName())
                .gender(p.getGender())
                .bibCollectedAt(p.getBibCollectedAt())
                .build();
    }

    private ParticipantStatisticsResponse emptyStatistics(Long eventId) {
        return ParticipantStatisticsResponse.builder()
                .eventId(eventId)
                .totalParticipants(0)
                .bibCollectedCount(0)
                .pendingCount(0)
                .raceBreakdown(new ArrayList<>())
                .categoryBreakdown(new ArrayList<>())
                .genderBreakdown(ParticipantStatisticsResponse.GenderStatistics.builder()
                        .male(0).female(0).other(0).build())
                .build();
    }

    @Override
    public long countParticipantsByCategoryId(Long eventId, Long categoryId) {
        log.info("Counting participants for event ID: {} and category ID: {}", eventId, categoryId);

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":categoryId", AttributeValue.builder().s(categoryId.toString()).build());

        Map<String, String> expressionNames = new HashMap<>();
        expressionNames.put("#categoryId", "categoryId");

        Expression filterExpression = Expression.builder()
                .expression("#categoryId = :categoryId")
                .expressionNames(expressionNames)
                .expressionValues(expressionValues)
                .build();

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(eventId.toString()).build()
        );

        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .build();

        long count = participantTable.query(queryRequest).stream()
                .mapToLong(page -> page.items().size())
                .sum();

        log.info("Found {} participants for category ID: {} in event ID: {}", count, categoryId, eventId);
        return count;
    }
}
