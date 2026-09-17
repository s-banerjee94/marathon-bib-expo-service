package com.timekeeper.bibexpo.distribution.service.impl;

import com.timekeeper.bibexpo.distribution.exception.BibAlreadyCollectedException;
import com.timekeeper.bibexpo.distribution.exception.BibNotCollectedException;
import com.timekeeper.bibexpo.distribution.exception.GoodiesAlreadyDistributedException;
import com.timekeeper.bibexpo.distribution.exception.GoodiesItemNotFoundException;
import com.timekeeper.bibexpo.distribution.model.dto.request.BulkCollectBibRequest;
import com.timekeeper.bibexpo.distribution.model.dto.request.BulkDistributeGoodiesRequest;
import com.timekeeper.bibexpo.distribution.model.dto.request.CollectBibRequest;
import com.timekeeper.bibexpo.distribution.model.dto.request.DistributeGoodiesRequest;
import com.timekeeper.bibexpo.distribution.model.dto.response.BibDistributionResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.BulkDistributionResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.DistributionLogListResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.DistributionLogResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.GoodiesDistributionResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.PendingParticipantListResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.UndoDistributionResponse;
import com.timekeeper.bibexpo.distribution.model.dynamodb.DistributionLogDDB;
import com.timekeeper.bibexpo.distribution.model.enums.LogSearchType;
import com.timekeeper.bibexpo.distribution.model.enums.PendingType;
import com.timekeeper.bibexpo.distribution.repository.DistributionLogDDBRepository;
import com.timekeeper.bibexpo.distribution.service.DistributionService;
import com.timekeeper.bibexpo.distribution.service.util.DistributionConstants;
import com.timekeeper.bibexpo.distribution.service.validator.DistributionValidator;
import com.timekeeper.bibexpo.messaging.campaign.service.ParticipantEventSmsService;
import com.timekeeper.bibexpo.messaging.campaign.service.ParticipantEventWhatsAppService;
import com.timekeeper.bibexpo.distribution.model.dto.response.DistributionGoodieResponse;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.model.entity.EventGoodie;
import com.timekeeper.bibexpo.event.model.entity.GoodieSource;
import com.timekeeper.bibexpo.inventory.api.GoodieIssue;
import com.timekeeper.bibexpo.inventory.api.GoodieIssueRecorder;
import com.timekeeper.bibexpo.inventory.api.GoodieRequest;
import com.timekeeper.bibexpo.inventory.api.GoodieStockOption;
import com.timekeeper.bibexpo.inventory.api.GoodieStockQuery;
import com.timekeeper.bibexpo.participant.api.ParticipantStore;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.participant.service.util.DistributorStamp;
import com.timekeeper.bibexpo.participant.service.util.ParticipantCountersMapper;
import com.timekeeper.bibexpo.event.api.EventStatsQuery;
import com.timekeeper.bibexpo.event.api.EventStatsRecorder;
import com.timekeeper.bibexpo.event.api.EventStore;
import com.timekeeper.bibexpo.event.api.ParticipantCounters;
import com.timekeeper.bibexpo.event.api.EventNames;
import com.timekeeper.bibexpo.event.api.RaceCategoryNameQuery;
import com.timekeeper.bibexpo.shared.error.ApiException;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.shared.persistence.DynamoDBPaginationCodec;
import com.timekeeper.bibexpo.shared.util.EventTimeUtil;
import com.timekeeper.bibexpo.shared.util.TextUtils;
import com.timekeeper.bibexpo.user.model.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributionServiceImpl implements DistributionService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Expression BIB_NOT_COLLECTED = Expression.builder()
            .expression("attribute_not_exists(bibCollectedAt) OR bibCollectedAt = :nullValue")
            .expressionValues(Map.of(":nullValue", AttributeValue.builder().nul(true).build()))
            .build();
    private static final Expression BIB_COLLECTED = Expression.builder()
            .expression("attribute_type(bibCollectedAt, :string)")
            .expressionValues(Map.of(":string", AttributeValue.builder().s("S").build()))
            .build();

    private final EventStore eventStore;
    private final ParticipantStore participantStore;
    private final DistributionLogDDBRepository logRepository;
    private final DynamoDBPaginationCodec paginationCodec;
    private final DistributionValidator validator;
    private final ParticipantEventSmsService participantEventSmsService;
    private final ParticipantEventWhatsAppService participantEventWhatsAppService;
    private final EventStatsRecorder eventStatsRecorder;
    private final EventStatsQuery eventStatsQuery;
    private final RaceCategoryNameQuery nameResolver;
    private final GoodieStockQuery goodieStockQuery;
    private final GoodieIssueRecorder goodieIssueRecorder;
    private final ObjectMapper objectMapper;

    @Override
    public BibDistributionResponse collectBib(Long eventId, String bibNumber, CollectBibRequest request, User currentUser) {
        return collectBib(requireDistributableEvent(eventId, currentUser), bibNumber, request, currentUser);
    }

    private BibDistributionResponse collectBib(Event event, String bibNumber, CollectBibRequest request,
                                               User currentUser) {
        Long eventId = event.getId();
        ParticipantDDB participant = participantStore.findByEventAndBibOrThrow(eventId, bibNumber);

        if (participant.getBibCollectedAt() != null) {
            throw new BibAlreadyCollectedException();
        }

        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String collectorName = TextUtils.toUpperOrNull(
                (request != null && request.getCollectorName() != null)
                        ? request.getCollectorName()
                        : participant.getFullName());
        String collectorPhone = (request != null && request.getCollectorPhone() != null)
                ? request.getCollectorPhone()
                : participant.getPhoneNumber();
        String distributedBy = DistributorStamp.of(currentUser.getId(), currentUser.getUsername());

        participant.setBibCollectedAt(now);
        participant.setBibCollectedByName(collectorName);
        participant.setBibCollectedByPhone(collectorPhone);
        participant.setBibDistributedBy(distributedBy);
        participant.setUpdatedAt(now);
        participant.setUpdatedBy(currentUser.getUsername());

        boolean withGoodies = request != null && request.getGoodiesItems() != null
                && !request.getGoodiesItems().isEmpty();
        List<HandOver> handed = withGoodies
                ? handOut(event, participant, request.getGoodiesItems(), request.getVariantIds(), now, distributedBy)
                : List.of();
        List<String> goodiesDistributed = handed.stream().map(HandOver::name).toList();
        if (!goodiesDistributed.isEmpty()) {
            logDistributionAction(String.valueOf(eventId), bibNumber,
                    DistributionConstants.ACTION_GOODIES_DISTRIBUTED,
                    goodiesDistributed, distributedBy, collectorName, collectorPhone, null);

            log.info("Goodies items {} distributed for bib {} in event {} by staff {}",
                    goodiesDistributed, bibNumber, eventId, distributedBy);
        }

        participantStore.save(participant);
        eventStatsRecorder.onBibCollected(ParticipantCountersMapper.of(participant), goodiesDistributed,
                EventTimeUtil.zoneOf(event.getTimezone()));
        markDistributionStarted(event);
        issueStock(eventId, bibNumber, handed, currentUser);

        logDistributionAction(String.valueOf(eventId), bibNumber,
                DistributionConstants.ACTION_BIB_COLLECTED,
                null, distributedBy, collectorName, collectorPhone, null);

        log.info("Bib {} collected for event {} by collector {} ({}), distributed by staff {}",
                bibNumber, eventId, collectorName, collectorPhone, distributedBy);

        participantEventSmsService.sendBibCollectedSms(event, participant);
        participantEventWhatsAppService.sendBibCollectedWhatsApp(event, participant);

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
        validator.validateDistributionAllowed(event);

        ParticipantDDB participant = participantStore.findByEventAndBibOrThrow(eventId, bibNumber);

        if (participant.getBibCollectedAt() == null) {
            throw new BibNotCollectedException();
        }

        ParticipantCounters beforeSnapshot = ParticipantCountersMapper.of(participant);
        List<GoodieIssue> taken = issuesIn(participant.getGoodiesDistribution());

        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String undoneBy = DistributorStamp.of(currentUser.getId(), currentUser.getUsername());

        participant.setBibCollectedAt(null);
        participant.setBibCollectedByName(null);
        participant.setBibCollectedByPhone(null);
        participant.setBibDistributedBy(null);
        participant.setGoodiesDistribution(new HashMap<>());
        participant.setUpdatedAt(now);
        participant.setUpdatedBy(currentUser.getUsername());

        participantStore.save(participant);
        eventStatsRecorder.onBibUndone(beforeSnapshot, EventTimeUtil.zoneOf(event.getTimezone()));
        if (!taken.isEmpty()) {
            postToInventory(eventId, bibNumber,
                    () -> goodieIssueRecorder.reverse(taken, bibNumber, currentUser.getUsername()));
        }

        logDistributionAction(String.valueOf(eventId), bibNumber,
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
        return distributeGoodies(requireDistributableEvent(eventId, currentUser), bibNumber, request, currentUser);
    }

    private GoodiesDistributionResponse distributeGoodies(Event event, String bibNumber,
                                                          DistributeGoodiesRequest request, User currentUser) {
        Long eventId = event.getId();
        ParticipantDDB participant = participantStore.findByEventAndBibOrThrow(eventId, bibNumber);

        if (participant.getBibCollectedAt() == null) {
            throw new BibNotCollectedException();
        }

        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String distributedBy = DistributorStamp.of(currentUser.getId(), currentUser.getUsername());

        List<HandOver> handed = handOut(event, participant, request.getGoodiesItems(), request.getVariantIds(),
                now, distributedBy);
        List<String> itemsDistributed = handed.stream().map(HandOver::name).toList();

        logDistributionAction(String.valueOf(eventId), bibNumber,
                DistributionConstants.ACTION_GOODIES_DISTRIBUTED,
                itemsDistributed, distributedBy, participant.getBibCollectedByName(),
                participant.getBibCollectedByPhone(), null);

        log.info("Goodies items {} distributed for bib {} in event {} by staff {}",
                itemsDistributed, bibNumber, eventId, distributedBy);

        participant.setUpdatedAt(now);
        participant.setUpdatedBy(currentUser.getUsername());

        participantStore.save(participant);
        eventStatsRecorder.onGoodiesDistributed(ParticipantCountersMapper.of(participant), itemsDistributed);
        markDistributionStarted(event);
        issueStock(eventId, bibNumber, handed, currentUser);

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
    public PendingParticipantListResponse getPending(Long eventId, PendingType type, Integer limit,
                                                     String lastEvaluatedKey, User currentUser) {
        requireVisibleEvent(eventId, currentUser);

        // DynamoDB tests whether the bib is collected. Goodies still owed are the participant's own list minus
        // what was handed over, which a filter expression cannot compare, so that test runs here.
        // ponytail: with few runners pending, one page can read the whole event; a sparse GSI reads only them.
        boolean bib = type == PendingType.BIB;
        Predicate<ParticipantDDB> keep = bib ? participant -> true : this::owesOwnGoodies;
        Page<ParticipantDDB> page = participantStore.fillPage(eventId, normalizeLimit(limit),
                decodeCursor(lastEvaluatedKey), bib ? BIB_NOT_COLLECTED : BIB_COLLECTED, keep);

        EventNames names = nameResolver.forEvent(eventId);
        List<PendingParticipantListResponse.PendingParticipant> participants = page.items().stream()
                .map(participant -> toPendingParticipant(participant, names))
                .toList();

        String newLastEvaluatedKey = encodeCursor(page.lastEvaluatedKey());
        return PendingParticipantListResponse.builder()
                .participants(participants)
                .lastEvaluatedKey(newLastEvaluatedKey)
                .count(participants.size())
                .totalPending(bib ? eventStatsQuery.pendingBibCount(eventId)
                        : eventStatsQuery.pendingGoodiesCount(eventId))
                .hasMore(newLastEvaluatedKey != null)
                .build();
    }

    @Override
    public DistributionLogListResponse getDistributionLogs(Long eventId, Integer limit, String lastEvaluatedKey, User currentUser) {
        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForLogAccess(currentUser, event);

        DistributionLogListResponse response = queryLogs(null,
                QueryConditional.keyEqualTo(Key.builder().partitionValue(String.valueOf(eventId)).build()),
                limit, lastEvaluatedKey);

        log.info("Retrieved {} distribution logs for event {}, hasMore: {}",
                response.getCount(), eventId, response.getHasMore());

        return response;
    }

    @Override
    public List<DistributionLogResponse> getParticipantLogs(Long eventId, String bibNumber, User currentUser) {
        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForLogAccess(currentUser, event);

        Key key = Key.builder()
                .partitionValue(String.valueOf(eventId))
                .sortValue(bibNumber)
                .build();

        // Exact match, not beginsWith: this is one participant's history, and a prefix returned
        // every bib that started with the one asked for.
        List<DistributionLogResponse> logs = new ArrayList<>();
        logRepository.getTable().index("LSI-BibNumberIndex").query(r -> r.queryConditional(
                QueryConditional.keyEqualTo(key)
        )).stream().flatMap(page -> page.items().stream()).forEach(logEntry ->
                logs.add(DistributionLogResponse.from(logEntry))
        );

        log.info("Retrieved {} distribution logs for participant {} in event {}", logs.size(), bibNumber, eventId);

        return logs;
    }

    @Override
    public BulkDistributionResponse bulkCollectBib(Long eventId, BulkCollectBibRequest request, User currentUser) {
        Event event = requireDistributableEvent(eventId, currentUser);
        return runEach(request.getItems(), BulkCollectBibRequest.CollectItem::getBibNumber,
                BulkCollectBibRequest.CollectItem::getGoodiesItems, item -> {
                    collectBib(event, item.getBibNumber(), CollectBibRequest.builder()
                            .collectorName(request.getCollectorName())
                            .collectorPhone(request.getCollectorPhone())
                            .goodiesItems(item.getGoodiesItems())
                            .variantIds(item.getVariantIds())
                            .build(), currentUser);
                    return item.getBibNumber();
                });
    }

    @Override
    public List<DistributionGoodieResponse> listGoodies(Long eventId, User currentUser) {
        Event event = requireVisibleEvent(eventId, currentUser);

        Map<String, GoodieStockOption> links = new HashMap<>();
        goodieStockQuery.optionsFor(eventId).forEach(link -> links.put(TextUtils.toMatchKey(link.goodieName()), link));
        return event.getEventGoodies().stream()
                .map(goodie -> toCounterGoodie(goodie, links.get(TextUtils.toMatchKey(goodie.name()))))
                .toList();
    }

    @Override
    public BulkDistributionResponse bulkDistributeGoodies(Long eventId, BulkDistributeGoodiesRequest request, User currentUser) {
        Event event = requireDistributableEvent(eventId, currentUser);
        return runEach(request.getItems(), BulkDistributeGoodiesRequest.DistributionItem::getBibNumber,
                BulkDistributeGoodiesRequest.DistributionItem::getGoodiesItems, item -> {
                    distributeGoodies(event, item.getBibNumber(), DistributeGoodiesRequest.builder()
                            .goodiesItems(item.getGoodiesItems())
                            .variantIds(item.getVariantIds())
                            .build(), currentUser);
                    return item.getBibNumber() + ":" + String.join(",", item.getGoodiesItems());
                });
    }

    @Override
    public DistributionLogListResponse lookupLogs(Long eventId, LogSearchType searchType, String searchValue,
                                                   Integer limit, String lastEvaluatedKey, User currentUser) {
        log.info("Lookup logs for event ID: {} with searchType: {}, searchValue: '{}' by user: {}",
                eventId, searchType, searchValue, currentUser.getUsername());

        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForLogAccess(currentUser, event);

        String indexName = getLogIndexName(searchType);

        // Collector names are stored upper-cased, so the search term must match that casing.
        String normalizedSearchValue = searchType == LogSearchType.COLLECTOR
                ? searchValue.trim().toUpperCase()
                : searchValue.trim();

        DistributionLogListResponse response = queryLogs(indexName,
                QueryConditional.sortBeginsWith(Key.builder()
                        .partitionValue(String.valueOf(eventId))
                        .sortValue(normalizedSearchValue)
                        .build()),
                limit, lastEvaluatedKey);

        log.info("Lookup logs completed for event {} using index {}. Found {} logs, hasMore: {}",
                eventId, indexName, response.getCount(), response.getHasMore());

        return response;
    }

    /**
     * One newest-first page of log rows, off the table itself when {@code indexName} is null or
     * off one of its local secondary indexes otherwise.
     */
    private DistributionLogListResponse queryLogs(String indexName, QueryConditional conditional,
                                                  Integer limit, String lastEvaluatedKey) {
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(conditional)
                .scanIndexForward(false)
                .limit(normalizeLimit(limit));

        applyCursor(requestBuilder, lastEvaluatedKey);

        Page<DistributionLogDDB> page = (indexName == null
                ? logRepository.getTable().query(requestBuilder.build()).stream()
                : logRepository.getTable().index(indexName).query(requestBuilder.build()).stream())
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
                .map(DistributionLogResponse::from)
                .toList();

        String newLastEvaluatedKey = encodeCursor(page.lastEvaluatedKey());

        return DistributionLogListResponse.builder()
                .logs(logs)
                .lastEvaluatedKey(newLastEvaluatedKey)
                .count(logs.size())
                .hasMore(newLastEvaluatedKey != null)
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
        return eventStore.requireById(eventId);
    }

    private Event requireVisibleEvent(Long eventId, User currentUser) {
        Event event = findEventOrThrow(eventId);
        validator.validateUserAuthorizationForEvent(currentUser, event);
        return event;
    }

    private Event requireDistributableEvent(Long eventId, User currentUser) {
        Event event = requireVisibleEvent(eventId, currentUser);
        validator.validateDistributionAllowed(event);
        return event;
    }

    // Each entry runs as its own single operation: one that fails is reported and does not stop the rest.
    private static <T> BulkDistributionResponse runEach(List<T> entries, Function<T, String> bibOf,
                                                        Function<T, List<String>> goodiesOf,
                                                        Function<T, String> run) {
        List<String> successful = new ArrayList<>();
        List<BulkDistributionResponse.FailedOperation> failed = new ArrayList<>();
        for (T entry : entries) {
            try {
                successful.add(run.apply(entry));
            } catch (RuntimeException e) {
                failed.add(failedOperation(bibOf.apply(entry), goodiesOf.apply(entry), e));
            }
        }
        return BulkDistributionResponse.builder()
                .successCount(successful.size())
                .successful(successful)
                .failed(failed)
                .build();
    }

    private void markDistributionStarted(Event event) {
        if (!Boolean.TRUE.equals(event.getDistributionStarted())) {
            event.setDistributionStarted(true);
            eventStore.markDistributionStarted(event.getId());
        }
    }

    // Bulk operations report per-bib failures in the response body, so only messages written for a user go in.
    // Those refusals are routine at a counter and log one line; anything unexpected keeps its stack.
    private static BulkDistributionResponse.FailedOperation failedOperation(String bibNumber, List<String> goodies,
                                                                            RuntimeException e) {
        boolean expected = e instanceof BibAlreadyCollectedException
                || e instanceof BibNotCollectedException
                || e instanceof GoodiesAlreadyDistributedException
                || e instanceof GoodiesItemNotFoundException
                || e instanceof ApiException;
        if (expected) {
            log.warn("Bulk operation refused bib {}: {}", bibNumber, e.getMessage());
        } else {
            log.error("Bulk operation failed for bib {}", bibNumber, e);
        }
        return BulkDistributionResponse.FailedOperation.builder()
                .bibNumber(bibNumber)
                .itemNames(goodies == null || goodies.isEmpty() ? null : goodies)
                .reason(expected && e.getMessage() != null ? e.getMessage() : "This bib could not be processed.")
                .build();
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (limit < 1) {
            throw new InvalidUserDataException("Please ask for at least one entry per page.");
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private void applyCursor(QueryEnhancedRequest.Builder requestBuilder, String lastEvaluatedKey) {
        Map<String, AttributeValue> exclusiveStartKey = decodeCursor(lastEvaluatedKey);
        if (exclusiveStartKey != null) {
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }
    }

    private Map<String, AttributeValue> decodeCursor(String lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        Map<String, AttributeValue> exclusiveStartKey = paginationCodec.decode(lastEvaluatedKey);
        return (exclusiveStartKey == null || exclusiveStartKey.isEmpty()) ? null : exclusiveStartKey;
    }

    // The shared codec returns "" for an exhausted page rather than null, and every response here
    // derives hasMore from the cursor being null. Left as-is the pending lists always answered
    // hasMore=true and a client paging until it flipped never stopped.
    private String encodeCursor(Map<String, AttributeValue> lastEvaluatedKey) {
        String encoded = paginationCodec.encode(lastEvaluatedKey);
        return (encoded == null || encoded.isEmpty()) ? null : encoded;
    }

    private boolean owesOwnGoodies(ParticipantDDB participant) {
        return !calculatePendingItems(participant.getGoodies(), participant.getGoodiesDistribution()).isEmpty();
    }

    private PendingParticipantListResponse.PendingParticipant toPendingParticipant(ParticipantDDB participant,
                                                                                  EventNames names) {
        return PendingParticipantListResponse.PendingParticipant.builder()
                .eventId(participant.getEventId())
                .bibNumber(participant.getBibNumber())
                .fullName(participant.getFullName())
                .email(participant.getEmail())
                .phoneNumber(participant.getPhoneNumber())
                .raceName(names.raceLabel(participant.getRaceId()))
                .categoryName(names.categoryLabel(participant.getCategoryId()))
                .bibCollectedAt(participant.getBibCollectedAt())
                .goodies(participant.getGoodies())
                .goodiesDistribution(participant.getGoodiesDistribution())
                .pendingItems(calculatePendingItems(participant.getGoodies(), participant.getGoodiesDistribution()))
                .build();
    }

    // Compared by name, not by count: a goody added by hand is on no participant's list, so handing one
    // over must not hide a goody of their own that is still owed.
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

    /**
     * Records the requested goodies on the participant. Every goody is checked before any is recorded, so one
     * that fails leaves the participant exactly as it was, and a failed check names every goody that fails it.
     */
    private List<HandOver> handOut(Event event, ParticipantDDB participant, List<String> itemNames,
                                   Map<String, Long> variantIds, String now, String distributedBy) {
        Map<String, String> distribution = participant.getGoodiesDistribution() == null
                ? new HashMap<>() : participant.getGoodiesDistribution();

        List<String> names = new ArrayList<>(itemNames.size());
        List<String> unknown = new ArrayList<>();
        for (String requested : itemNames) {
            String name = recordedName(event, participant, requested);
            names.add(name);
            if (name == null) {
                unknown.add(requested);
            }
        }
        if (!unknown.isEmpty()) {
            throw new GoodiesItemNotFoundException(unknown);
        }

        Set<String> requestedOnce = new HashSet<>();
        List<String> handedAlready = names.stream()
                .filter(name -> distribution.containsKey(name) || !requestedOnce.add(name))
                .distinct()
                .toList();
        if (!handedAlready.isEmpty()) {
            throw new GoodiesAlreadyDistributedException(handedAlready);
        }

        // A participant's own value says which variant they are owed; a goody added by hand has none.
        Map<String, String> own = participant.getGoodies() == null ? Collections.emptyMap() : participant.getGoodies();
        List<GoodieRequest> requests = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            String requested = itemNames.get(i);
            requests.add(new GoodieRequest(names.get(i), own.get(requested),
                    variantIds == null ? null : variantIds.get(requested)));
        }
        Map<String, GoodieIssue> issues = goodieStockQuery.planIssues(event.getId(), requests);

        List<HandOver> handed = names.stream().map(name -> new HandOver(name, issues.get(name))).toList();
        handed.forEach(h -> distribution.put(h.name(), distributionRecord(now, distributedBy, h.issue())));
        participant.setGoodiesDistribution(distribution);
        return handed;
    }

    // A participant's own goodies are keyed exactly as their list spells them. A goody added by hand is on
    // no list, so it is matched against the event's and recorded under the event's spelling. Null for neither.
    private static String recordedName(Event event, ParticipantDDB participant, String requested) {
        if (participant.getGoodies() != null && participant.getGoodies().containsKey(requested)) {
            return requested;
        }
        String wanted = TextUtils.toMatchKey(requested);
        return event.getEventGoodies().stream()
                .filter(goodie -> goodie.source() == GoodieSource.MANUAL
                        && TextUtils.toMatchKey(goodie.name()).equals(wanted))
                .map(EventGoodie::name)
                .findFirst()
                .orElse(null);
    }

    private String distributionRecord(String collectedAt, String distributedBy, GoodieIssue issue) {
        return objectMapper.writeValueAsString(issue == null
                ? new DistributionRecord(collectedAt, distributedBy, null, null, null)
                : new DistributionRecord(collectedAt, distributedBy,
                        issue.variantId(), issue.variantLabel(), issue.locationId()));
    }

    private List<GoodieIssue> issuesIn(Map<String, String> distribution) {
        if (distribution == null) {
            return List.of();
        }
        return distribution.values().stream()
                .map(value -> objectMapper.readValue(value, DistributionRecord.class).toIssue())
                .filter(Objects::nonNull)
                .toList();
    }

    private void issueStock(Long eventId, String bibNumber, List<HandOver> handed, User currentUser) {
        List<GoodieIssue> issues = handed.stream().map(HandOver::issue).filter(Objects::nonNull).toList();
        if (!issues.isEmpty()) {
            postToInventory(eventId, bibNumber,
                    () -> goodieIssueRecorder.issue(issues, bibNumber, currentUser.getUsername()));
        }
    }

    // The hand-over is already saved by the time stock moves, and the counter never waits on inventory:
    // a failure here is logged for an adjustment to correct, not shown to the runner.
    private void postToInventory(Long eventId, String bibNumber, Runnable post) {
        try {
            post.run();
        } catch (RuntimeException e) {
            log.error("Could not post goodies stock for bib {} in event {}; the shelf needs an adjustment",
                    bibNumber, eventId, e);
        }
    }

    // Offered for an imported goody too: its participant's value may be one the item was never taught, or
    // they may swap sizes at the counter.
    private static DistributionGoodieResponse toCounterGoodie(EventGoodie goodie, GoodieStockOption link) {
        boolean choose = link != null && link.variants().size() > 1;
        return DistributionGoodieResponse.builder()
                .name(goodie.name())
                .source(goodie.source())
                .itemName(link == null ? null : link.itemName())
                .variants(choose
                        ? link.variants().stream()
                                .map(v -> DistributionGoodieResponse.Variant.builder()
                                        .variantId(v.variantId())
                                        .label(v.label())
                                        .build())
                                .toList()
                        : List.of())
                .build();
    }

    /** One goody being handed over, and what it takes out of inventory; null when it takes nothing. */
    private record HandOver(String name, GoodieIssue issue) {
    }

    // The value kept against each goody in goodiesDistribution. The variant and location are there only
    // when the goody came out of inventory, which is what lets an undo put it back where it came from.
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DistributionRecord(String collectedAt, String distributedBy, Long variantId, String variantLabel,
                              Long locationId) {

        GoodieIssue toIssue() {
            return variantId == null || locationId == null ? null
                    : new GoodieIssue(variantId, variantLabel, locationId);
        }
    }

    // The log table is keyed by (eventId, timestamp). A whole-second timestamp made that key
    // collide -- a bib collected together with its goodies wrote both rows at the same instant
    // and the second putItem overwrote the first -- so log rows carry their own microsecond
    // stamp, distinct from the whole-second one recorded on the participant. Fixed width: a
    // variable number of fraction digits would not sort lexicographically.
    private static final DateTimeFormatter LOG_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private void logDistributionAction(String eventId, String bibNumber,
                                       String action, List<String> itemNames, String performedBy,
                                       String collectorName, String collectorPhone, String details) {
        DistributionLogDDB log = DistributionLogDDB.builder()
                .eventId(eventId)
                .timestamp(LOG_TIMESTAMP.format(Instant.now()))
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

}
