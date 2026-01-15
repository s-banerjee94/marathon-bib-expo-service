package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.*;
import com.timekeeper.bibexpo.model.dto.request.BulkCollectBibRequest;
import com.timekeeper.bibexpo.model.dto.request.BulkDistributeGoodiesRequest;
import com.timekeeper.bibexpo.model.dto.request.CollectBibRequest;
import com.timekeeper.bibexpo.model.dto.request.DistributeGoodiesRequest;
import com.timekeeper.bibexpo.model.dto.response.BibDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.BulkDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.DistributionLogResponse;
import com.timekeeper.bibexpo.model.dto.response.GoodiesDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.PendingBibListResponse;
import com.timekeeper.bibexpo.model.dto.response.PendingGoodiesListResponse;
import com.timekeeper.bibexpo.model.dto.response.UndoDistributionResponse;
import com.timekeeper.bibexpo.model.dynamodb.DistributionLogDDB;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.DistributionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributionServiceImpl implements DistributionService {

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final EventRepository eventRepository;
    private final JsonMapper objectMapper;

    private DynamoDbTable<ParticipantDDB> participantTable;
    private DynamoDbTable<DistributionLogDDB> distributionLogTable;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String ACTION_BIB_COLLECTED = "BIB_COLLECTED";
    private static final String ACTION_BIB_UNDONE = "BIB_UNDONE";
    private static final String ACTION_GOODIES_DISTRIBUTED = "GOODIES_DISTRIBUTED";

    @PostConstruct
    public void init() {
        this.participantTable = dynamoDbEnhancedClient.table(
                "marathon-participants",
                TableSchema.fromBean(ParticipantDDB.class)
        );
        this.distributionLogTable = dynamoDbEnhancedClient.table(
                "marathon-distribution-logs",
                TableSchema.fromBean(DistributionLogDDB.class)
        );
    }

    @Override
    public BibDistributionResponse collectBib(Long eventId, String bibNumber, CollectBibRequest request, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        String eventIdStr = String.valueOf(eventId);
        Key key = Key.builder()
                .partitionValue(eventIdStr)
                .sortValue(bibNumber)
                .build();

        ParticipantDDB participant = participantTable.getItem(key);
        if (participant == null) {
            throw new ParticipantNotFoundException(eventIdStr, bibNumber);
        }

        if (participant.getBibCollectedAt() != null) {
            throw new BibAlreadyCollectedException(eventIdStr, bibNumber);
        }

        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String collectorName = (request != null && request.getCollectorName() != null)
                ? request.getCollectorName()
                : participant.getFullName();
        String collectorPhone = (request != null && request.getCollectorPhone() != null)
                ? request.getCollectorPhone()
                : participant.getPhoneNumber();
        String distributedBy = currentUser.getId() + "__|__" + currentUser.getUsername();

        participant.setBibCollectedAt(now);
        participant.setBibCollectedByName(collectorName);
        participant.setBibCollectedByPhone(collectorPhone);
        participant.setBibDistributedBy(distributedBy);
        participant.setUpdatedAt(now);
        participant.setUpdatedBy(currentUser.getUsername());

        List<String> goodiesDistributed = new ArrayList<>();
        if (request != null && request.getGoodiesItems() != null && !request.getGoodiesItems().isEmpty()) {
            Map<String, String> goodiesDistribution = participant.getGoodiesDistribution();
            if (goodiesDistribution == null) {
                goodiesDistribution = new HashMap<>();
            }

            for (String itemName : request.getGoodiesItems()) {
                if (participant.getGoodies() == null || !participant.getGoodies().containsKey(itemName)) {
                    throw new GoodiesItemNotFoundException(itemName, bibNumber);
                }

                if (goodiesDistribution.containsKey(itemName)) {
                    throw new GoodiesAlreadyDistributedException(itemName, bibNumber);
                }

                String distributionData = String.format("{\"collectedAt\":\"%s\",\"distributedBy\":\"%s\"}",
                        now, distributedBy);
                goodiesDistribution.put(itemName, distributionData);
                goodiesDistributed.add(itemName);

                logDistributionAction(eventIdStr, bibNumber, now, ACTION_GOODIES_DISTRIBUTED,
                        itemName, distributedBy, collectorName, collectorPhone, null);

                log.info("Goodies item '{}' distributed for bib {} in event {} by staff {}",
                        itemName, bibNumber, eventId, distributedBy);
            }

            participant.setGoodiesDistribution(goodiesDistribution);
        }

        participantTable.putItem(participant);

        logDistributionAction(eventIdStr, bibNumber, now, ACTION_BIB_COLLECTED,
                null, distributedBy, collectorName, collectorPhone, null);

        log.info("Bib {} collected for event {} by collector {} ({}), distributed by staff {}",
                bibNumber, eventId, collectorName, collectorPhone, distributedBy);

        return BibDistributionResponse.builder()
                .success(true)
                .bibNumber(bibNumber)
                .collectedAt(now)
                .collectedByName(collectorName)
                .collectedByPhone(collectorPhone)
                .distributedByUserId(currentUser.getId())
                .distributedByUsername(currentUser.getUsername())
                .goodiesDistributed(goodiesDistributed.isEmpty() ? null : goodiesDistributed)
                .build();
    }

    @Override
    public UndoDistributionResponse undoBib(Long eventId, String bibNumber, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForUndoOperation(currentUser, event);

        String eventIdStr = String.valueOf(eventId);
        Key key = Key.builder()
                .partitionValue(eventIdStr)
                .sortValue(bibNumber)
                .build();

        ParticipantDDB participant = participantTable.getItem(key);
        if (participant == null) {
            throw new ParticipantNotFoundException(eventIdStr, bibNumber);
        }

        if (participant.getBibCollectedAt() == null) {
            throw new BibNotCollectedException(eventIdStr, bibNumber);
        }

        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String undoneBy = currentUser.getId() + "__|__" + currentUser.getUsername();

        participant.setBibCollectedAt(null);
        participant.setBibCollectedByName(null);
        participant.setBibCollectedByPhone(null);
        participant.setBibDistributedBy(null);
        participant.setGoodiesDistribution(new HashMap<>());
        participant.setUpdatedAt(now);
        participant.setUpdatedBy(currentUser.getUsername());

        participantTable.putItem(participant);

        logDistributionAction(eventIdStr, bibNumber, now, ACTION_BIB_UNDONE,
                null, undoneBy, null, null, "Bib collection undone. All goodies distribution reset.");

        log.info("Bib {} collection undone for event {} by user {}. All goodies distribution reset.",
                bibNumber, eventId, undoneBy);

        return UndoDistributionResponse.builder()
                .success(true)
                .message("Bib collection undone successfully")
                .bibNumber(bibNumber)
                .undoneAt(now)
                .undoneByUserId(currentUser.getId())
                .undoneByUsername(currentUser.getUsername())
                .build();
    }

    private void logDistributionAction(String eventId, String bibNumber, String timestamp,
                                       String action, String itemName, String performedBy,
                                       String collectorName, String collectorPhone, String details) {
        DistributionLogDDB log = DistributionLogDDB.builder()
                .eventId(eventId)
                .timestamp(timestamp)
                .bibNumber(bibNumber)
                .action(action)
                .itemName(itemName)
                .performedBy(performedBy)
                .collectorName(collectorName)
                .collectorPhone(collectorPhone)
                .details(details)
                .build();

        distributionLogTable.putItem(log);
    }

    private void validateUserAuthorizationForEvent(User currentUser, Event event) {
        if (Boolean.FALSE.equals(event.getEnabled())) {
            throw new EventDisabledException("Event is disabled. Distribution operations are not allowed for event ID: " + event.getId());
        }

        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (role == UserRole.ORGANIZER_ADMIN || role == UserRole.ORGANIZER_USER || role == UserRole.DISTRIBUTOR) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException("User does not belong to any organization");
            }

            if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
                throw new UnauthorizedAccessException(
                        "User can only access events from their own organization");
            }
            return;
        }

        throw new UnauthorizedAccessException("User does not have permission to access events");
    }

    @Override
    public GoodiesDistributionResponse distributeGoodies(Long eventId, String bibNumber, DistributeGoodiesRequest request, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        String eventIdStr = String.valueOf(eventId);
        Key key = Key.builder()
                .partitionValue(eventIdStr)
                .sortValue(bibNumber)
                .build();

        ParticipantDDB participant = participantTable.getItem(key);
        if (participant == null) {
            throw new ParticipantNotFoundException(eventIdStr, bibNumber);
        }

        if (participant.getBibCollectedAt() == null) {
            throw new BibNotCollectedException(eventIdStr, bibNumber);
        }

        Map<String, String> goodiesDistribution = participant.getGoodiesDistribution();
        if (goodiesDistribution == null) {
            goodiesDistribution = new HashMap<>();
        }

        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String distributedBy = currentUser.getId() + "__|__" + currentUser.getUsername();

        List<String> itemsDistributed = new ArrayList<>();
        for (String itemName : request.getGoodiesItems()) {
            if (participant.getGoodies() == null || !participant.getGoodies().containsKey(itemName)) {
                throw new GoodiesItemNotFoundException(itemName, bibNumber);
            }

            if (goodiesDistribution.containsKey(itemName)) {
                throw new GoodiesAlreadyDistributedException(itemName, bibNumber);
            }

            String distributionData = String.format("{\"collectedAt\":\"%s\",\"distributedBy\":\"%s\"}",
                    now, distributedBy);
            goodiesDistribution.put(itemName, distributionData);
            itemsDistributed.add(itemName);

            logDistributionAction(eventIdStr, bibNumber, now, ACTION_GOODIES_DISTRIBUTED,
                    itemName, distributedBy, participant.getBibCollectedByName(),
                    participant.getBibCollectedByPhone(), null);

            log.info("Goodies item '{}' distributed for bib {} in event {} by staff {}",
                    itemName, bibNumber, eventId, distributedBy);
        }

        participant.setGoodiesDistribution(goodiesDistribution);
        participant.setUpdatedAt(now);
        participant.setUpdatedBy(currentUser.getUsername());

        participantTable.putItem(participant);

        return GoodiesDistributionResponse.builder()
                .success(true)
                .bibNumber(bibNumber)
                .itemsDistributed(itemsDistributed)
                .distributedAt(now)
                .distributedByUserId(currentUser.getId())
                .distributedByUsername(currentUser.getUsername())
                .build();
    }

    @Override
    public PendingBibListResponse getPendingBibs(Long eventId, Integer limit, String lastEvaluatedKey, User currentUser) {
        log.info("Fetching pending bibs for event ID: {} with limit: {} by user: {}",
                eventId, limit, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        String eventIdStr = String.valueOf(eventId);

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":nullValue", AttributeValue.builder().nul(true).build());

        Expression filterExpression = Expression.builder()
                .expression("attribute_not_exists(bibCollectedAt) OR bibCollectedAt = :nullValue")
                .expressionValues(expressionValues)
                .build();

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(eventIdStr).build()
        );

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .limit(limit != null ? limit : 50);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            try {
                Map<String, AttributeValue> exclusiveStartKey = decodeLastEvaluatedKey(lastEvaluatedKey);
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            } catch (Exception e) {
                log.error("Failed to decode pagination key", e);
                throw new InvalidUserDataException("Invalid pagination key");
            }
        }

        Page<ParticipantDDB> page = participantTable.query(requestBuilder.build()).stream().findFirst().orElse(null);

        if (page == null) {
            return PendingBibListResponse.builder()
                    .participants(Collections.emptyList())
                    .count(0)
                    .hasMore(false)
                    .build();
        }

        List<ParticipantDistributionResponse> participants = page.items().stream()
                .map(participant -> ParticipantDistributionResponse.builder()
                        .eventId(participant.getEventId())
                        .bibNumber(participant.getBibNumber())
                        .fullName(participant.getFullName())
                        .email(participant.getEmail())
                        .phoneNumber(participant.getPhoneNumber())
                        .raceName(participant.getRaceName())
                        .categoryName(participant.getCategoryName())
                        .bibCollectedAt(null)
                        .bibCollectedByName(null)
                        .bibCollectedByPhone(null)
                        .bibDistributedBy(null)
                        .goodies(participant.getGoodies())
                        .goodiesDistribution(new HashMap<>())
                        .build())
                .toList();

        String newLastEvaluatedKey = null;
        if (page.lastEvaluatedKey() != null && !page.lastEvaluatedKey().isEmpty()) {
            newLastEvaluatedKey = encodeLastEvaluatedKey(page.lastEvaluatedKey());
        }

        log.info("Found {} participants with pending bib collection for event {}", participants.size(), eventId);

        return PendingBibListResponse.builder()
                .participants(participants)
                .lastEvaluatedKey(newLastEvaluatedKey)
                .count(participants.size())
                .hasMore(newLastEvaluatedKey != null)
                .build();
    }

    private String encodeLastEvaluatedKey(Map<String, AttributeValue> lastEvaluatedKey) {
        try {
            Map<String, Object> simpleMap = new HashMap<>();
            for (Map.Entry<String, AttributeValue> entry : lastEvaluatedKey.entrySet()) {
                simpleMap.put(entry.getKey(), attributeValueToObject(entry.getValue()));
            }
            String json = objectMapper.writeValueAsString(simpleMap);
            return Base64.getEncoder().encodeToString(json.getBytes());
        } catch (Exception e) {
            log.error("Failed to encode lastEvaluatedKey", e);
            return null;
        }
    }

    private Map<String, AttributeValue> decodeLastEvaluatedKey(String encodedKey) {
        try {
            String json = new String(Base64.getDecoder().decode(encodedKey));
            Map<String, Object> simpleMap = objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));

            Map<String, AttributeValue> attributeMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : simpleMap.entrySet()) {
                attributeMap.put(entry.getKey(), objectToAttributeValue(entry.getValue()));
            }
            return attributeMap;
        } catch (Exception e) {
            log.error("Failed to decode pagination key: {}", encodedKey, e);
            throw new InvalidUserDataException("Invalid pagination key format");
        }
    }

    private Object attributeValueToObject(AttributeValue attributeValue) {
        if (attributeValue.s() != null) {
            return Map.of("S", attributeValue.s());
        } else if (attributeValue.n() != null) {
            return Map.of("N", attributeValue.n());
        } else if (attributeValue.bool() != null) {
            return Map.of("BOOL", attributeValue.bool());
        }
        return Map.of("NULL", true);
    }

    private AttributeValue objectToAttributeValue(Object value) {
        if (!(value instanceof Map)) {
            throw new InvalidUserDataException("Invalid attribute value format");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;

        if (map.containsKey("S")) {
            return AttributeValue.builder().s((String) map.get("S")).build();
        } else if (map.containsKey("N")) {
            return AttributeValue.builder().n((String) map.get("N")).build();
        } else if (map.containsKey("BOOL")) {
            return AttributeValue.builder().bool((Boolean) map.get("BOOL")).build();
        } else if (map.containsKey("NULL")) {
            return AttributeValue.builder().nul(true).build();
        }

        throw new InvalidUserDataException("Unsupported attribute value type");
    }

    @Override
    public List<DistributionLogResponse> getDistributionLogs(Long eventId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForLogAccess(currentUser, event);

        String eventIdStr = String.valueOf(eventId);
        Key key = Key.builder()
                .partitionValue(eventIdStr)
                .build();

        List<DistributionLogResponse> logs = new ArrayList<>();
        distributionLogTable.query(r -> r.queryConditional(
                software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.keyEqualTo(key)
        )).items().forEach(logEntry ->
            logs.add(convertToLogResponse(logEntry))
        );

        log.info("Retrieved {} distribution logs for event {}", logs.size(), eventId);

        return logs;
    }

    @Override
    public List<DistributionLogResponse> getParticipantLogs(Long eventId, String bibNumber, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForLogAccess(currentUser, event);

        String eventIdStr = String.valueOf(eventId);
        Key key = Key.builder()
                .partitionValue(eventIdStr)
                .sortValue(bibNumber)
                .build();

        List<DistributionLogResponse> logs = new ArrayList<>();
        distributionLogTable.index("LSI-BibNumberIndex").query(r -> r.queryConditional(
                software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.sortBeginsWith(key)
        )).stream().flatMap(page -> page.items().stream()).forEach(logEntry ->
            logs.add(convertToLogResponse(logEntry))
        );

        log.info("Retrieved {} distribution logs for participant {} in event {}", logs.size(), bibNumber, eventId);

        return logs;
    }

    private DistributionLogResponse convertToLogResponse(DistributionLogDDB logEntry) {
        return DistributionLogResponse.builder()
                .eventId(logEntry.getEventId())
                .timestamp(logEntry.getTimestamp())
                .bibNumber(logEntry.getBibNumber())
                .action(logEntry.getAction())
                .itemName(logEntry.getItemName())
                .performedBy(logEntry.getPerformedBy())
                .collectorName(logEntry.getCollectorName())
                .collectorPhone(logEntry.getCollectorPhone())
                .details(logEntry.getDetails())
                .build();
    }

    private void validateUserAuthorizationForUndoOperation(User currentUser, Event event) {
        if (Boolean.FALSE.equals(event.getEnabled())) {
            throw new EventDisabledException("Event is disabled. Distribution operations are not allowed for event ID: " + event.getId());
        }

        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (role == UserRole.ORGANIZER_ADMIN || role == UserRole.ORGANIZER_USER) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException("User does not belong to any organization");
            }

            if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
                throw new UnauthorizedAccessException(
                        "User can only access events from their own organization");
            }
            return;
        }

        throw new UnauthorizedAccessException(
                "User does not have permission to undo distribution operations. Only ROOT, ADMIN, ORGANIZER_ADMIN, and ORGANIZER_USER can perform undo operations.");
    }

    private void validateUserAuthorizationForLogAccess(User currentUser, Event event) {
        if (Boolean.FALSE.equals(event.getEnabled())) {
            throw new EventDisabledException("Event is disabled. Distribution operations are not allowed for event ID: " + event.getId());
        }

        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (role == UserRole.ORGANIZER_ADMIN) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException("User does not belong to any organization");
            }

            if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
                throw new UnauthorizedAccessException(
                        "User can only access events from their own organization");
            }
            return;
        }

        throw new UnauthorizedAccessException(
                "User does not have permission to access distribution logs. Only ROOT, ADMIN, and ORGANIZER_ADMIN can access logs.");
    }

    @Override
    public ParticipantDistributionResponse getDistributionStatus(Long eventId, String bibNumber, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        String eventIdStr = String.valueOf(eventId);
        Key key = Key.builder()
                .partitionValue(eventIdStr)
                .sortValue(bibNumber)
                .build();

        ParticipantDDB participant = participantTable.getItem(key);
        if (participant == null) {
            throw new ParticipantNotFoundException(eventIdStr, bibNumber);
        }

        return ParticipantDistributionResponse.builder()
                .eventId(participant.getEventId())
                .bibNumber(participant.getBibNumber())
                .fullName(participant.getFullName())
                .email(participant.getEmail())
                .phoneNumber(participant.getPhoneNumber())
                .raceName(participant.getRaceName())
                .categoryName(participant.getCategoryName())
                .bibCollectedAt(participant.getBibCollectedAt())
                .bibCollectedByName(participant.getBibCollectedByName())
                .bibCollectedByPhone(participant.getBibCollectedByPhone())
                .bibDistributedBy(participant.getBibDistributedBy())
                .goodies(participant.getGoodies())
                .goodiesDistribution(participant.getGoodiesDistribution())
                .build();
    }

    @Override
    public PendingGoodiesListResponse getPendingGoodies(Long eventId, Integer limit, String lastEvaluatedKey, User currentUser) {
        log.info("Fetching pending goodies for event ID: {} with limit: {} by user: {}",
                eventId, limit, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        String eventIdStr = String.valueOf(eventId);

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(eventIdStr).build()
        );

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit != null ? limit : 50);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            try {
                Map<String, AttributeValue> exclusiveStartKey = decodeLastEvaluatedKey(lastEvaluatedKey);
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            } catch (Exception e) {
                log.error("Failed to decode pagination key", e);
                throw new InvalidUserDataException("Invalid pagination key");
            }
        }

        Page<ParticipantDDB> page = participantTable.query(requestBuilder.build()).stream().findFirst().orElse(null);

        if (page == null) {
            return PendingGoodiesListResponse.builder()
                    .participants(Collections.emptyList())
                    .count(0)
                    .hasMore(false)
                    .build();
        }

        List<PendingGoodiesListResponse.ParticipantPendingGoodies> participants = page.items().stream()
                .filter(participant -> participant.getBibCollectedAt() != null)
                .filter(participant -> {
                    Map<String, String> goodies = participant.getGoodies();
                    Map<String, String> distribution = participant.getGoodiesDistribution();
                    if (goodies == null || goodies.isEmpty()) {
                        return false;
                    }
                    if (distribution == null) {
                        return true;
                    }
                    return goodies.size() > distribution.size();
                })
                .map(participant -> {
                    Map<String, String> goodies = participant.getGoodies();
                    Map<String, String> distribution = participant.getGoodiesDistribution();
                    List<String> pendingItems = new ArrayList<>();

                    if (goodies != null) {
                        for (String itemName : goodies.keySet()) {
                            if (distribution == null || !distribution.containsKey(itemName)) {
                                pendingItems.add(itemName);
                            }
                        }
                    }

                    return PendingGoodiesListResponse.ParticipantPendingGoodies.builder()
                            .eventId(participant.getEventId())
                            .bibNumber(participant.getBibNumber())
                            .fullName(participant.getFullName())
                            .email(participant.getEmail())
                            .phoneNumber(participant.getPhoneNumber())
                            .raceName(participant.getRaceName())
                            .categoryName(participant.getCategoryName())
                            .bibCollectedAt(participant.getBibCollectedAt())
                            .goodies(participant.getGoodies())
                            .goodiesDistribution(participant.getGoodiesDistribution())
                            .pendingItems(pendingItems)
                            .build();
                })
                .toList();

        String newLastEvaluatedKey = null;
        if (page.lastEvaluatedKey() != null && !page.lastEvaluatedKey().isEmpty()) {
            newLastEvaluatedKey = encodeLastEvaluatedKey(page.lastEvaluatedKey());
        }

        log.info("Found {} participants with pending goodies for event {}", participants.size(), eventId);

        return PendingGoodiesListResponse.builder()
                .participants(participants)
                .lastEvaluatedKey(newLastEvaluatedKey)
                .count(participants.size())
                .hasMore(newLastEvaluatedKey != null)
                .build();
    }

    @Override
    public BulkDistributionResponse bulkCollectBib(Long eventId, BulkCollectBibRequest request, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        List<String> successful = new ArrayList<>();
        List<BulkDistributionResponse.FailedOperation> failed = new ArrayList<>();

        for (String bibNumber : request.getBibNumbers()) {
            try {
                CollectBibRequest collectRequest = CollectBibRequest.builder()
                        .collectorName(request.getCollectorName())
                        .collectorPhone(request.getCollectorPhone())
                        .build();

                collectBib(eventId, bibNumber, collectRequest, currentUser);
                successful.add(bibNumber);
            } catch (Exception e) {
                log.warn("Failed to collect bib {} in bulk operation: {}", bibNumber, e.getMessage());
                failed.add(BulkDistributionResponse.FailedOperation.builder()
                        .bibNumber(bibNumber)
                        .reason(e.getMessage())
                        .build());
            }
        }

        log.info("Bulk bib collection completed for event {}: {} successful, {} failed",
                eventId, successful.size(), failed.size());

        return BulkDistributionResponse.builder()
                .successCount(successful.size())
                .successful(successful)
                .failed(failed)
                .build();
    }

    @Override
    public BulkDistributionResponse bulkDistributeGoodies(Long eventId, BulkDistributeGoodiesRequest request, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        List<String> successful = new ArrayList<>();
        List<BulkDistributionResponse.FailedOperation> failed = new ArrayList<>();

        for (BulkDistributeGoodiesRequest.DistributionItem item : request.getItems()) {
            try {
                DistributeGoodiesRequest distributeRequest = DistributeGoodiesRequest.builder()
                        .goodiesItems(item.getGoodiesItems())
                        .build();

                distributeGoodies(eventId, item.getBibNumber(), distributeRequest, currentUser);
                successful.add(item.getBibNumber() + ":" + String.join(",", item.getGoodiesItems()));
            } catch (Exception e) {
                log.warn("Failed to distribute goodies items {} for bib {} in bulk operation: {}",
                        item.getGoodiesItems(), item.getBibNumber(), e.getMessage());
                failed.add(BulkDistributionResponse.FailedOperation.builder()
                        .bibNumber(item.getBibNumber())
                        .itemName(String.join(",", item.getGoodiesItems()))
                        .reason(e.getMessage())
                        .build());
            }
        }

        log.info("Bulk goodies distribution completed for event {}: {} successful, {} failed",
                eventId, successful.size(), failed.size());

        return BulkDistributionResponse.builder()
                .successCount(successful.size())
                .successful(successful)
                .failed(failed)
                .build();
    }
}
