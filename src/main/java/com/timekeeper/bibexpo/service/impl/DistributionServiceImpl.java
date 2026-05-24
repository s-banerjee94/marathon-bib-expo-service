package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.*;
import com.timekeeper.bibexpo.model.dto.response.DistributionLogListResponse;
import com.timekeeper.bibexpo.model.enums.LogSearchType;
import com.timekeeper.bibexpo.model.dto.request.BulkCollectBibRequest;
import com.timekeeper.bibexpo.model.dto.request.BulkDistributeGoodiesRequest;
import com.timekeeper.bibexpo.model.dto.request.CollectBibRequest;
import com.timekeeper.bibexpo.model.dto.request.DistributeGoodiesRequest;
import com.timekeeper.bibexpo.model.dto.response.*;
import com.timekeeper.bibexpo.model.dynamodb.DistributionLogDDB;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.dynamodb.DistributionLogDDBRepository;
import com.timekeeper.bibexpo.repository.dynamodb.ParticipantDDBRepository;
import com.timekeeper.bibexpo.service.DistributionService;
import com.timekeeper.bibexpo.service.EventStatsService;
import com.timekeeper.bibexpo.service.SmsSendService;
import com.timekeeper.bibexpo.service.util.DistributionConstants;
import com.timekeeper.bibexpo.service.util.DynamoDBPaginationCodec;
import com.timekeeper.bibexpo.service.validator.DistributionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributionServiceImpl implements DistributionService {

    private final EventRepository eventRepository;
    private final com.timekeeper.bibexpo.service.EventService eventService;
    private final ParticipantDDBRepository participantRepository;
    private final DistributionLogDDBRepository logRepository;
    private final DynamoDBPaginationCodec paginationCodec;
    private final DistributionValidator validator;
    private final SmsSendService smsSendService;
    private final EventStatsService eventStatsService;

    @Override
    public BibDistributionResponse collectBib(Long eventId, String bibNumber, CollectBibRequest request, User currentUser) {
        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        ParticipantDDB participant = participantRepository.findByEventAndBibOrThrow(eventId, bibNumber);

        if (participant.getBibCollectedAt() != null) {
            throw new BibAlreadyCollectedException(String.valueOf(eventId), bibNumber);
        }

        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String collectorName = (request != null && request.getCollectorName() != null)
                ? request.getCollectorName()
                : participant.getFullName();
        String collectorPhone = (request != null && request.getCollectorPhone() != null)
                ? request.getCollectorPhone()
                : participant.getPhoneNumber();
        String distributedBy = DistributionConstants.formatDistributorInfo(currentUser.getId(), currentUser.getUsername());

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
                validateGoodiesItem(participant, goodiesDistribution, itemName, bibNumber);

                String distributionData = String.format("{\"collectedAt\":\"%s\",\"distributedBy\":\"%s\"}",
                        now, distributedBy);
                goodiesDistribution.put(itemName, distributionData);
                goodiesDistributed.add(itemName);
            }

            logDistributionAction(String.valueOf(eventId), bibNumber, now,
                    DistributionConstants.ACTION_GOODIES_DISTRIBUTED,
                    goodiesDistributed, distributedBy, collectorName, collectorPhone, null);

            log.info("Goodies items {} distributed for bib {} in event {} by staff {}",
                    goodiesDistributed, bibNumber, eventId, distributedBy);

            participant.setGoodiesDistribution(goodiesDistribution);
        }

        participantRepository.save(participant);
        eventStatsService.onBibCollected(participant, goodiesDistributed);

        logDistributionAction(String.valueOf(eventId), bibNumber, now,
                DistributionConstants.ACTION_BIB_COLLECTED,
                null, distributedBy, collectorName, collectorPhone, null);

        log.info("Bib {} collected for event {} by collector {} ({}), distributed by staff {}",
                bibNumber, eventId, collectorName, collectorPhone, distributedBy);

        smsSendService.sendBibCollectedSms(event, participant);

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
        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForUndoOperation(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        ParticipantDDB participant = participantRepository.findByEventAndBibOrThrow(eventId, bibNumber);

        if (participant.getBibCollectedAt() == null) {
            throw new BibNotCollectedException(String.valueOf(eventId), bibNumber);
        }

        ParticipantDDB beforeSnapshot = snapshotForStats(participant);

        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String undoneBy = DistributionConstants.formatDistributorInfo(currentUser.getId(), currentUser.getUsername());

        participant.setBibCollectedAt(null);
        participant.setBibCollectedByName(null);
        participant.setBibCollectedByPhone(null);
        participant.setBibDistributedBy(null);
        participant.setGoodiesDistribution(new HashMap<>());
        participant.setUpdatedAt(now);
        participant.setUpdatedBy(currentUser.getUsername());

        participantRepository.save(participant);
        eventStatsService.onBibUndone(beforeSnapshot);

        logDistributionAction(String.valueOf(eventId), bibNumber, now,
                DistributionConstants.ACTION_BIB_UNDONE,
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

    @Override
    public GoodiesDistributionResponse distributeGoodies(Long eventId, String bibNumber,
                                                         DistributeGoodiesRequest request, User currentUser) {
        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        ParticipantDDB participant = participantRepository.findByEventAndBibOrThrow(eventId, bibNumber);

        if (participant.getBibCollectedAt() == null) {
            throw new BibNotCollectedException(String.valueOf(eventId), bibNumber);
        }

        Map<String, String> goodiesDistribution = participant.getGoodiesDistribution();
        if (goodiesDistribution == null) {
            goodiesDistribution = new HashMap<>();
        }

        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String distributedBy = DistributionConstants.formatDistributorInfo(currentUser.getId(), currentUser.getUsername());

        List<String> itemsDistributed = new ArrayList<>();
        for (String itemName : request.getGoodiesItems()) {
            validateGoodiesItem(participant, goodiesDistribution, itemName, bibNumber);

            String distributionData = String.format("{\"collectedAt\":\"%s\",\"distributedBy\":\"%s\"}",
                    now, distributedBy);
            goodiesDistribution.put(itemName, distributionData);
            itemsDistributed.add(itemName);
        }

        logDistributionAction(String.valueOf(eventId), bibNumber, now,
                DistributionConstants.ACTION_GOODIES_DISTRIBUTED,
                itemsDistributed, distributedBy, participant.getBibCollectedByName(),
                participant.getBibCollectedByPhone(), null);

        log.info("Goodies items {} distributed for bib {} in event {} by staff {}",
                itemsDistributed, bibNumber, eventId, distributedBy);

        participant.setGoodiesDistribution(goodiesDistribution);
        participant.setUpdatedAt(now);
        participant.setUpdatedBy(currentUser.getUsername());

        participantRepository.save(participant);
        eventStatsService.onGoodiesDistributed(participant, itemsDistributed);

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

        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":nullValue", AttributeValue.builder().nul(true).build());

        Expression filterExpression = Expression.builder()
                .expression("attribute_not_exists(bibCollectedAt) OR bibCollectedAt = :nullValue")
                .expressionValues(expressionValues)
                .build();

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(String.valueOf(eventId)).build()
        );

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .limit(limit != null ? limit : 50);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = paginationCodec.decode(lastEvaluatedKey);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        Page<ParticipantDDB> page = participantRepository.getTable()
                .query(requestBuilder.build())
                .stream()
                .findFirst()
                .orElse(null);

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

        String newLastEvaluatedKey = paginationCodec.encode(page.lastEvaluatedKey());

        log.info("Found {} participants with pending bib collection for event {}", participants.size(), eventId);

        return PendingBibListResponse.builder()
                .participants(participants)
                .lastEvaluatedKey(newLastEvaluatedKey)
                .count(participants.size())
                .hasMore(newLastEvaluatedKey != null)
                .build();
    }

    @Override
    public DistributionLogListResponse getDistributionLogs(Long eventId, Integer limit, String lastEvaluatedKey, User currentUser) {
        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForLogAccess(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(String.valueOf(eventId)).build()))
                .scanIndexForward(false)
                .limit(limit != null ? Math.min(limit, 100) : 50);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = paginationCodec.decode(lastEvaluatedKey);
            if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        Page<DistributionLogDDB> page = logRepository.getTable()
                .query(requestBuilder.build())
                .stream()
                .findFirst()
                .orElse(null);

        if (page == null) {
            return DistributionLogListResponse.builder()
                    .logs(Collections.emptyList())
                    .count(0)
                    .hasMore(false)
                    .build();
        }

        List<DistributionLogResponse> logs = page.items().stream()
                .map(this::convertToLogResponse)
                .toList();

        String newLastEvaluatedKey = null;
        if (page.lastEvaluatedKey() != null && !page.lastEvaluatedKey().isEmpty()) {
            newLastEvaluatedKey = paginationCodec.encode(page.lastEvaluatedKey());
        }

        log.info("Retrieved {} distribution logs for event {}, hasMore: {}", logs.size(), eventId, newLastEvaluatedKey != null);

        return DistributionLogListResponse.builder()
                .logs(logs)
                .lastEvaluatedKey(newLastEvaluatedKey)
                .count(logs.size())
                .hasMore(newLastEvaluatedKey != null)
                .build();
    }

    @Override
    public List<DistributionLogResponse> getParticipantLogs(Long eventId, String bibNumber, User currentUser) {
        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForLogAccess(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        Key key = Key.builder()
                .partitionValue(String.valueOf(eventId))
                .sortValue(bibNumber)
                .build();

        List<DistributionLogResponse> logs = new ArrayList<>();
        logRepository.getTable().index("LSI-BibNumberIndex").query(r -> r.queryConditional(
                QueryConditional.sortBeginsWith(key)
        )).stream().flatMap(page -> page.items().stream()).forEach(logEntry ->
                logs.add(convertToLogResponse(logEntry))
        );

        log.info("Retrieved {} distribution logs for participant {} in event {}", logs.size(), bibNumber, eventId);

        return logs;
    }

    @Override
    public ParticipantDistributionResponse getDistributionStatus(Long eventId, String bibNumber, User currentUser) {
        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        ParticipantDDB participant = participantRepository.findByEventAndBibOrThrow(eventId, bibNumber);

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

        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        Page<ParticipantDDB> page = queryParticipantsWithPagination(eventId, limit, lastEvaluatedKey);

        if (page == null) {
            return buildEmptyPendingGoodiesResponse();
        }

        List<PendingGoodiesListResponse.ParticipantPendingGoodies> participants = page.items().stream()
                .filter(participant -> participant.getBibCollectedAt() != null)
                .filter(this::hasPendingGoodies)
                .map(this::mapToParticipantPendingGoodies)
                .toList();

        String newLastEvaluatedKey = paginationCodec.encode(page.lastEvaluatedKey());

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
        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

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
        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

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
                        .itemNames(item.getGoodiesItems())
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

    @Override
    public DistributionLogListResponse lookupLogs(Long eventId, LogSearchType searchType, String searchValue,
                                                   Integer limit, String lastEvaluatedKey, User currentUser) {
        log.info("Lookup logs for event ID: {} with searchType: {}, searchValue: '{}' by user: {}",
                eventId, searchType, searchValue, currentUser.getUsername());

        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForLogAccess(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        int effectiveLimit = limit != null ? Math.min(limit, 100) : 50;
        String indexName = getLogIndexName(searchType);

        QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(String.valueOf(eventId))
                        .sortValue(searchValue.trim())
                        .build()
        );

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false)
                .limit(effectiveLimit);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = paginationCodec.decode(lastEvaluatedKey);
            if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        Page<DistributionLogDDB> page = logRepository.getTable().index(indexName)
                .query(requestBuilder.build())
                .stream()
                .findFirst()
                .orElse(null);

        if (page == null) {
            return DistributionLogListResponse.builder()
                    .logs(Collections.emptyList())
                    .count(0)
                    .hasMore(false)
                    .build();
        }

        List<DistributionLogResponse> logs = page.items().stream()
                .map(this::convertToLogResponse)
                .toList();

        String newLastEvaluatedKey = null;
        if (page.lastEvaluatedKey() != null && !page.lastEvaluatedKey().isEmpty()) {
            newLastEvaluatedKey = paginationCodec.encode(page.lastEvaluatedKey());
        }

        log.info("Lookup logs completed for event {} using index {}. Found {} logs, hasMore: {}",
                eventId, indexName, logs.size(), newLastEvaluatedKey != null);

        return DistributionLogListResponse.builder()
                .logs(logs)
                .lastEvaluatedKey(newLastEvaluatedKey)
                .count(logs.size())
                .hasMore(newLastEvaluatedKey != null)
                .build();
    }

    private ParticipantDDB snapshotForStats(ParticipantDDB p) {
        Map<String, String> goodiesCopy = p.getGoodiesDistribution() != null
                ? new HashMap<>(p.getGoodiesDistribution())
                : null;
        return ParticipantDDB.builder()
                .eventId(p.getEventId())
                .bibNumber(p.getBibNumber())
                .raceId(p.getRaceId())
                .raceName(p.getRaceName())
                .categoryId(p.getCategoryId())
                .categoryName(p.getCategoryName())
                .gender(p.getGender())
                .bibCollectedAt(p.getBibCollectedAt())
                .goodiesDistribution(goodiesCopy)
                .build();
    }

    private String getLogIndexName(LogSearchType searchType) {
        return switch (searchType) {
            case BIB -> "LSI-BibNumberIndex";
            case ACTION -> "LSI-ActionIndex";
            case PERFORMED_BY -> "LSI-PerformedByIndex";
            case COLLECTOR -> "LSI-CollectorNameIndex";
            case COLLECTOR_PHONE -> "LSI-CollectorPhoneIndex";
        };
    }

    private Event findEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);
    }

    private Page<ParticipantDDB> queryParticipantsWithPagination(Long eventId, Integer limit, String lastEvaluatedKey) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(String.valueOf(eventId)).build()
        );

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit != null ? limit : 50);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = paginationCodec.decode(lastEvaluatedKey);
            if (!exclusiveStartKey.isEmpty()) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        return participantRepository.getTable()
                .query(requestBuilder.build())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private PendingGoodiesListResponse buildEmptyPendingGoodiesResponse() {
        return PendingGoodiesListResponse.builder()
                .participants(Collections.emptyList())
                .count(0)
                .hasMore(false)
                .build();
    }

    private boolean hasPendingGoodies(ParticipantDDB participant) {
        Map<String, String> goodies = participant.getGoodies();
        Map<String, String> distribution = participant.getGoodiesDistribution();

        if (goodies == null || goodies.isEmpty()) {
            return false;
        }
        if (distribution == null) {
            return true;
        }
        return goodies.size() > distribution.size();
    }

    private PendingGoodiesListResponse.ParticipantPendingGoodies mapToParticipantPendingGoodies(ParticipantDDB participant) {
        List<String> pendingItems = calculatePendingItems(participant.getGoodies(), participant.getGoodiesDistribution());

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
    }

    private List<String> calculatePendingItems(Map<String, String> goodies, Map<String, String> distribution) {
        if (goodies == null) {
            return Collections.emptyList();
        }

        List<String> pendingItems = new ArrayList<>();
        for (String itemName : goodies.keySet()) {
            if (distribution == null || !distribution.containsKey(itemName)) {
                pendingItems.add(itemName);
            }
        }
        return pendingItems;
    }

    private void validateGoodiesItem(ParticipantDDB participant, Map<String, String> goodiesDistribution,
                                      String itemName, String bibNumber) {
        if (participant.getGoodies() == null || !participant.getGoodies().containsKey(itemName)) {
            throw new GoodiesItemNotFoundException();
        }

        if (goodiesDistribution.containsKey(itemName)) {
            throw new GoodiesAlreadyDistributedException(itemName, bibNumber);
        }
    }

    private void logDistributionAction(String eventId, String bibNumber, String timestamp,
                                       String action, List<String> itemNames, String performedBy,
                                       String collectorName, String collectorPhone, String details) {
        DistributionLogDDB log = DistributionLogDDB.builder()
                .eventId(eventId)
                .timestamp(timestamp)
                .bibNumber(bibNumber)
                .action(action)
                .itemNames(itemNames)
                .performedBy(performedBy)
                .collectorName(collectorName)
                .collectorPhone(collectorPhone)
                .details(details)
                .build();

        logRepository.save(log);
    }

    private DistributionLogResponse convertToLogResponse(DistributionLogDDB logEntry) {
        return DistributionLogResponse.builder()
                .eventId(logEntry.getEventId())
                .timestamp(logEntry.getTimestamp())
                .bibNumber(logEntry.getBibNumber())
                .action(logEntry.getAction())
                .itemNames(logEntry.getItemNames())
                .performedBy(logEntry.getPerformedBy())
                .collectorName(logEntry.getCollectorName())
                .collectorPhone(logEntry.getCollectorPhone())
                .details(logEntry.getDetails())
                .build();
    }
}
