package com.timekeeper.bibexpo.participant.service.impl;

import com.timekeeper.bibexpo.exception.CategoryNotFoundException;
import com.timekeeper.bibexpo.exception.EventLimitExceededException;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventLimit;
import com.timekeeper.bibexpo.participant.exception.BibNumberAlreadyExistsException;
import com.timekeeper.bibexpo.participant.exception.ChipNumberAlreadyExistsException;
import com.timekeeper.bibexpo.participant.exception.ParticipantDeletionFailedException;
import com.timekeeper.bibexpo.participant.exception.RaceCategoryMismatchException;
import com.timekeeper.bibexpo.participant.model.dto.request.BulkDeleteParticipantsRequest;
import com.timekeeper.bibexpo.participant.model.dto.request.CreateParticipantRequest;
import com.timekeeper.bibexpo.participant.model.dto.request.UpdateParticipantRequest;
import com.timekeeper.bibexpo.participant.model.dto.response.DeleteParticipantsResponse;
import com.timekeeper.bibexpo.participant.model.dto.response.ParticipantListResponse;
import com.timekeeper.bibexpo.participant.model.dto.response.ParticipantResponse;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.participant.model.enums.SearchType;
import com.timekeeper.bibexpo.participant.repository.ParticipantDDBRepository;
import com.timekeeper.bibexpo.participant.service.ParticipantService;
import com.timekeeper.bibexpo.participant.service.validator.ParticipantAccessGuard;
import com.timekeeper.bibexpo.repository.dynamodb.EventStatsDDBRepository;
import com.timekeeper.bibexpo.repository.EventLimitRepository;
import com.timekeeper.bibexpo.service.CategoryService;
import com.timekeeper.bibexpo.service.EventStatsService;
import com.timekeeper.bibexpo.service.RaceService;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.shared.persistence.DynamoDBPaginationCodec;
import com.timekeeper.bibexpo.shared.util.TextUtils;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantServiceImpl implements ParticipantService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_LOOKUP_PAGE_SIZE = 100;

    private final ParticipantDDBRepository participantRepository;
    private final RaceService raceService;
    private final CategoryService categoryService;
    private final ParticipantAccessGuard accessGuard;
    private final DynamoDBPaginationCodec paginationCodec;
    private final EventStatsDDBRepository eventStatsRepo;
    private final EventStatsService eventStatsService;
    private final RaceCategoryNameResolver nameResolver;
    private final EventLimitRepository eventLimitRepository;

    @Override
    public ParticipantResponse createParticipant(Long eventId, CreateParticipantRequest request, User currentUser) {
        trimStringFields(request);

        log.info("Creating participant with BIB {} for event ID: {} by user: {}",
                request.getBibNumber(), eventId, currentUser.getUsername());

        accessGuard.forWrite(eventId, currentUser);

        EventLimit limits = eventLimitRepository.findByEventId(eventId)
                .orElseGet(() -> EventLimit.builder().build());
        long currentCount = eventStatsRepo.getTotalParticipantCount(eventId.toString());
        if (currentCount >= limits.getMaxParticipants()) {
            throw new EventLimitExceededException("You have reached the maximum number of participants allowed for this event.");
        }

        if (participantRepository.findByEventAndBib(eventId, request.getBibNumber()) != null) {
            throw new BibNumberAlreadyExistsException();
        }

        assertChipNumberAvailable(eventId, request.getChipNumber(), request.getBibNumber());

        // Validate the race/category exist for this event; names are resolved at read time.
        raceService.getRaceById(eventId, request.getRaceId(), currentUser);
        categoryService.getCategoryById(eventId, request.getRaceId(), request.getCategoryId(), currentUser);

        String timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();

        ParticipantDDB participant = ParticipantDDB.builder()
                .eventId(eventId.toString())
                .bibNumber(request.getBibNumber())
                .chipNumber(request.getChipNumber())
                .fullName(TextUtils.toUpperOrNull(request.getFullName()))
                .email(TextUtils.toLowerOrNull(request.getEmail()))
                .phoneNumber(request.getPhoneNumber())
                .dateOfBirth(request.getDateOfBirth())
                .age(request.getAge())
                .gender(request.getGender())
                .country(request.getCountry())
                .city(request.getCity())
                .raceId(request.getRaceId().toString())
                .categoryId(request.getCategoryId().toString())
                .raceCategoryKey(ParticipantDDB.compositeKey(
                        request.getRaceId().toString(), request.getCategoryId().toString()))
                .bibCollectedAt(request.getBibCollectedAt())
                .goodies(request.getGoodies() != null ? new HashMap<>(request.getGoodies()) : new HashMap<>())
                .additionalFields(request.getAdditionalFields() != null ? new HashMap<>(request.getAdditionalFields()) : new HashMap<>())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .notes(request.getNotes())
                .createdAt(timestamp)
                .createdBy(currentUser.getUsername())
                .updatedAt(timestamp)
                .updatedBy(currentUser.getUsername())
                .build();

        participantRepository.save(participant);
        eventStatsService.onParticipantCreated(participant);

        log.info("Successfully created participant with BIB {} for event ID: {}", request.getBibNumber(), eventId);

        return mapParticipantToResponse(participant, nameResolver.forEvent(eventId));
    }

    @Override
    public ParticipantListResponse getParticipantsByEvent(Long eventId, Integer limit, String lastEvaluatedKey, User currentUser) {
        log.info("Fetching participants for event ID: {} with limit: {} by user: {}",
                eventId, limit, currentUser.getUsername());

        accessGuard.forRead(eventId, currentUser);

        Page<ParticipantDDB> page = participantRepository.findPage(
                eventId, normalizeLimit(limit), decodeCursor(lastEvaluatedKey), null);

        return toListResponse(page, eventId);
    }

    @Override
    public ParticipantResponse getParticipantByBibNumber(Long eventId, String bibNumber, User currentUser) {
        log.info("Fetching participant with bib {} for event ID: {} by user: {}",
                bibNumber, eventId, currentUser.getUsername());

        accessGuard.forRead(eventId, currentUser);

        ParticipantDDB participant = participantRepository.findByEventAndBibOrThrow(eventId, bibNumber);

        log.info("Retrieved participant BIB {} from DynamoDB with {} goodies: {}",
                bibNumber, participant.getGoodies() != null ? participant.getGoodies().size() : 0,
                participant.getGoodies());

        return mapParticipantToResponse(participant, nameResolver.forEvent(eventId));
    }

    @Override
    public ParticipantResponse updateParticipant(Long eventId, String bibNumber,
                                                  UpdateParticipantRequest request, User currentUser) {
        trimStringFields(request);

        log.info("Updating participant BIB {} for event ID: {} by user: {}",
                bibNumber, eventId, currentUser.getUsername());

        accessGuard.forWrite(eventId, currentUser);

        ParticipantDDB participant = participantRepository.findByEventAndBibOrThrow(eventId, bibNumber);

        ParticipantDDB beforeSnapshot = snapshotForStats(participant);

        boolean bibNumberChanged = false;
        String newBibNumber = bibNumber;

        if (StringUtils.hasText(request.getNewBibNumber()) && !request.getNewBibNumber().equals(bibNumber)) {
            newBibNumber = request.getNewBibNumber();
            bibNumberChanged = true;

            if (participantRepository.findByEventAndBib(eventId, newBibNumber) != null) {
                throw new BibNumberAlreadyExistsException();
            }

            log.warn("BIB number change requested: {} -> {} for event {}",
                    bibNumber, newBibNumber, eventId);
        }

        if (StringUtils.hasText(request.getRaceId()) || StringUtils.hasText(request.getCategoryId())) {
            updateRaceAndCategory(participant, request, eventId, currentUser);
        }

        if (request.getChipNumber() != null && !request.getChipNumber().isBlank()
                && !request.getChipNumber().equals(participant.getChipNumber())) {
            assertChipNumberAvailable(eventId, request.getChipNumber(), bibNumber);
        }

        // Merge-patch: optional fields clear on a blank value; fullName/email keep their
        // case-normalisation; gender and bibCollectedAt are required/operational, so a blank
        // value is ignored rather than wiping them.
        TextUtils.applyIfSent(request.getChipNumber(), participant::setChipNumber);
        TextUtils.applyRequiredIfSent(request.getFullName(), v -> participant.setFullName(v.toUpperCase()));
        TextUtils.applyIfSent(request.getEmail(), v -> participant.setEmail(v == null ? null : v.toLowerCase()));
        TextUtils.applyIfSent(request.getPhoneNumber(), participant::setPhoneNumber);
        TextUtils.applyIfSent(request.getDateOfBirth(), participant::setDateOfBirth);
        TextUtils.applyIfSent(request.getAge(), participant::setAge);
        TextUtils.applyRequiredIfSent(request.getGender(), participant::setGender);
        TextUtils.applyIfSent(request.getCountry(), participant::setCountry);
        TextUtils.applyIfSent(request.getCity(), participant::setCity);
        TextUtils.applyIfSent(request.getEmergencyContactName(), participant::setEmergencyContactName);
        TextUtils.applyIfSent(request.getEmergencyContactPhone(), participant::setEmergencyContactPhone);
        TextUtils.applyIfSent(request.getNotes(), participant::setNotes);
        TextUtils.applyRequiredIfSent(request.getBibCollectedAt(), participant::setBibCollectedAt);

        if (request.getAdditionalFields() != null) {
            mergeAdditionalFields(participant, request.getAdditionalFields());
        }

        String timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        participant.setUpdatedAt(timestamp);
        participant.setUpdatedBy(currentUser.getUsername());

        if (bibNumberChanged) {
            participantRepository.deleteByEventAndBib(eventId, bibNumber);
            participant.setBibNumber(newBibNumber);
            participantRepository.save(participant);

            log.info("BIB number changed from {} to {} for event ID: {}",
                    bibNumber, newBibNumber, eventId);
        } else {
            participantRepository.save(participant);

            log.info("Successfully updated participant BIB {} for event ID: {}", bibNumber, eventId);
        }

        eventStatsService.onParticipantUpdated(beforeSnapshot, participant);

        return mapParticipantToResponse(participant, nameResolver.forEvent(eventId));
    }

    @Override
    public Long getParticipantCount(Long eventId, User currentUser) {
        log.info("Counting participants for event ID: {} by user: {}", eventId, currentUser.getUsername());

        accessGuard.forRead(eventId, currentUser);

        long count = eventStatsRepo.getTotalParticipantCount(eventId.toString());

        log.info("Found {} participants for event ID: {}", count, eventId);
        return count;
    }

    private ParticipantResponse mapParticipantToResponse(ParticipantDDB participant, EventNames names) {
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
                .raceName(names.raceLabel(participant.getRaceId()))
                .categoryId(participant.getCategoryId())
                .categoryName(names.categoryLabel(participant.getCategoryId()))
                .goodies(participant.getGoodies())
                .bibCollectedAt(participant.getBibCollectedAt())
                .bibCollectedByName(participant.getBibCollectedByName())
                .bibCollectedByPhone(participant.getBibCollectedByPhone())
                .bibDistributedBy(participant.getBibDistributedBy())
                .goodiesDistribution(participant.getGoodiesDistribution())
                .additionalFields(participant.getAdditionalFields())
                .emergencyContactName(participant.getEmergencyContactName())
                .emergencyContactPhone(participant.getEmergencyContactPhone())
                .notes(participant.getNotes())
                .createdAt(participant.getCreatedAt())
                .createdBy(participant.getCreatedBy())
                .updatedAt(participant.getUpdatedAt())
                .updatedBy(participant.getUpdatedBy())
                .build();
    }

    /** Maps a query page to a paginated response, enriching each row with current names. */
    private ParticipantListResponse toListResponse(Page<ParticipantDDB> page, Long eventId) {
        if (page == null) {
            return emptyListResponse();
        }
        EventNames names = nameResolver.forEvent(eventId);
        List<ParticipantResponse> participants = page.items().stream()
                .map(participant -> mapParticipantToResponse(participant, names))
                .toList();
        String lastKey = encodeCursor(page.lastEvaluatedKey());
        return ParticipantListResponse.builder()
                .participants(participants)
                .lastEvaluatedKey(lastKey)
                .count(participants.size())
                .hasMore(lastKey != null)
                .build();
    }

    private ParticipantListResponse emptyListResponse() {
        return ParticipantListResponse.builder()
                .participants(Collections.emptyList())
                .count(0)
                .hasMore(false)
                .build();
    }

    @Override
    public DeleteParticipantsResponse deleteParticipant(Long eventId, String bibNumber, User currentUser) {
        log.info("Deleting participant with bib {} for event ID: {} by user: {}", bibNumber, eventId, currentUser.getUsername());

        Event event = accessGuard.forDelete(eventId, currentUser);

        ParticipantDDB participant = participantRepository.findByEventAndBibOrThrow(eventId, bibNumber);

        participantRepository.deleteByEventAndBib(eventId, bibNumber);
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

        Event event = accessGuard.forDelete(eventId, currentUser);

        int deletedCount = participantRepository.deleteAllByEventId(eventId.toString());
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

        Event event = accessGuard.forDelete(eventId, currentUser);

        // The request DTO carries the same bound, so over HTTP this never fires; it holds the line
        // for callers that reach the service directly.
        if (bibNumbers.size() > BulkDeleteParticipantsRequest.MAX_BIB_NUMBERS) {
            throw new InvalidUserDataException("You cannot delete more than 25 participants at once.");
        }

        List<String> notFoundBibs = new ArrayList<>();
        List<ParticipantDDB> participantsToDelete = new ArrayList<>();

        for (String bibNumber : bibNumbers) {
            ParticipantDDB participant = participantRepository.findByEventAndBib(eventId, bibNumber);
            if (participant == null) {
                notFoundBibs.add(bibNumber);
            } else {
                participantsToDelete.add(participant);
            }
        }

        int deletedCount = 0;
        int failedCount = notFoundBibs.size();

        if (!participantsToDelete.isEmpty()) {
            try {
                deletedCount = participantRepository.deleteAll(participantsToDelete);
                failedCount += participantsToDelete.size() - deletedCount;
                eventStatsService.onBulkDeleted(participantsToDelete);
            } catch (Exception e) {
                log.error("Failed to delete participants in bulk for event {}", eventId, e);
                throw new ParticipantDeletionFailedException(e);
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

        boolean categorySent = StringUtils.hasText(request.getCategoryId());
        String newRaceId = StringUtils.hasText(request.getRaceId()) ? request.getRaceId() : participant.getRaceId();
        String newCategoryId = categorySent ? request.getCategoryId() : participant.getCategoryId();

        // Validate the new race/category exist for this event; names are resolved at read time.
        raceService.getRaceById(eventId, Long.parseLong(newRaceId), currentUser);
        try {
            categoryService.getCategoryById(eventId, Long.parseLong(newRaceId),
                    Long.parseLong(newCategoryId), currentUser);
        } catch (CategoryNotFoundException ex) {
            // The caller only moved the race, so the category they never sent is the one that no
            // longer fits — report the mismatch instead of a category they did not ask for.
            if (!categorySent) {
                throw new RaceCategoryMismatchException();
            }
            throw ex;
        }

        participant.setRaceId(newRaceId);
        participant.setCategoryId(newCategoryId);
        participant.setRaceCategoryKey(ParticipantDDB.compositeKey(newRaceId, newCategoryId));
    }

    private void mergeAdditionalFields(ParticipantDDB participant, Map<String, String> updates) {
        Map<String, String> current = participant.getAdditionalFields() != null
                ? participant.getAdditionalFields()
                : new HashMap<>();
        updates.forEach((key, value) -> {
            if (value == null || value.isBlank()) {
                current.remove(key);
            } else {
                current.put(key, value);
            }
        });
        participant.setAdditionalFields(current);
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

        accessGuard.forRead(eventId, currentUser);

        if (searchValue == null || searchValue.trim().isEmpty()) {
            throw new InvalidUserDataException("Please enter something to search for.");
        }

        if (searchType == SearchType.BIB) {
            return lookupByBibNumber(eventId, searchValue.trim());
        }

        String indexName = getIndexNameForSearchType(searchType);
        Page<ParticipantDDB> page = participantRepository.findPageByIndex(
                indexName,
                buildLookupConditional(eventId, searchType, searchValue.trim()),
                Math.min(normalizeLimit(limit), MAX_LOOKUP_PAGE_SIZE),
                decodeCursor(lastEvaluatedKey));

        ParticipantListResponse response = toListResponse(page, eventId);

        log.info("Lookup completed for event ID: {} using index {}. Found {} participants, hasMore: {}",
                eventId, indexName, response.getCount(), response.getHasMore());

        return response;
    }

    private ParticipantListResponse lookupByBibNumber(Long eventId, String bibNumber) {
        ParticipantDDB participant = participantRepository.findByEventAndBib(eventId, bibNumber);

        if (participant == null) {
            return emptyListResponse();
        }

        return ParticipantListResponse.builder()
                .participants(List.of(mapParticipantToResponse(participant, nameResolver.forEvent(eventId))))
                .count(1)
                .hasMore(false)
                .build();
    }

    private String getIndexNameForSearchType(SearchType searchType) {
        return switch (searchType) {
            case NAME -> ParticipantDDB.FULL_NAME_INDEX;
            case EMAIL -> ParticipantDDB.EMAIL_INDEX;
            case PHONE -> ParticipantDDB.PHONE_NUMBER_INDEX;
            case RACE, CATEGORY -> ParticipantDDB.RACE_CATEGORY_INDEX;
            case BIB -> throw new IllegalArgumentException("BIB search does not use an index");
        };
    }

    /**
     * RACE matches every participant in a race via begins_with on the "raceId#" prefix of the
     * composite key. CATEGORY targets one race+category and expects the full "raceId#categoryId"
     * composite as the search value, matched exactly.
     */
    private QueryConditional buildLookupConditional(Long eventId, SearchType searchType, String searchValue) {
        String partition = eventId.toString();
        return switch (searchType) {
            case NAME -> QueryConditional.sortBeginsWith(
                    Key.builder().partitionValue(partition).sortValue(TextUtils.toUpperOrNull(searchValue)).build());
            case EMAIL -> QueryConditional.sortBeginsWith(
                    Key.builder().partitionValue(partition).sortValue(TextUtils.toLowerOrNull(searchValue)).build());
            case PHONE -> QueryConditional.sortBeginsWith(
                    Key.builder().partitionValue(partition).sortValue(searchValue).build());
            case RACE -> QueryConditional.sortBeginsWith(
                    Key.builder().partitionValue(partition)
                            .sortValue(searchValue + ParticipantDDB.KEY_DELIMITER).build());
            case CATEGORY -> QueryConditional.keyEqualTo(
                    Key.builder().partitionValue(partition).sortValue(searchValue).build());
            case BIB -> throw new IllegalArgumentException("BIB search does not use an index");
        };
    }

    /**
     * Enforces per-event chip-number uniqueness. A null/blank chip is exempt (multiple participants
     * may have no chip). Throws if the chip is already held by a different bib.
     */
    private void assertChipNumberAvailable(Long eventId, String chipNumber, String ownerBibNumber) {
        if (chipNumber == null || chipNumber.isBlank()) {
            return;
        }
        if (participantRepository.isChipNumberTakenByAnother(eventId, chipNumber, ownerBibNumber)) {
            throw new ChipNumberAlreadyExistsException();
        }
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (limit < 1) {
            throw new InvalidUserDataException("Please ask for at least one entry per page.");
        }
        return limit;
    }

    private Map<String, AttributeValue> decodeCursor(String lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        try {
            return paginationCodec.decode(lastEvaluatedKey);
        } catch (Exception e) {
            log.error("Failed to decode pagination key", e);
            throw new InvalidUserDataException("Invalid page token. Please start from the first page.");
        }
    }

    // The shared codec returns "" for an exhausted page rather than null, and hasMore is derived
    // from the cursor being null — see the same guard in distribution.
    private String encodeCursor(Map<String, AttributeValue> lastEvaluatedKey) {
        String encoded = paginationCodec.encode(lastEvaluatedKey);
        return (encoded == null || encoded.isEmpty()) ? null : encoded;
    }

    private static void trimStringFields(CreateParticipantRequest r) {
        r.setChipNumber(TextUtils.trimToNull(r.getChipNumber()));
        r.setBibNumber(TextUtils.trimToNull(r.getBibNumber()));
        r.setFullName(TextUtils.trimToNull(r.getFullName()));
        r.setGender(TextUtils.trimToNull(r.getGender()));
        r.setPhoneNumber(TextUtils.trimToNull(r.getPhoneNumber()));
        r.setEmail(TextUtils.trimToNull(r.getEmail()));
        r.setDateOfBirth(TextUtils.trimToNull(r.getDateOfBirth()));
        r.setCountry(TextUtils.trimToNull(r.getCountry()));
        r.setCity(TextUtils.trimToNull(r.getCity()));
        r.setBibCollectedAt(TextUtils.trimToNull(r.getBibCollectedAt()));
        r.setEmergencyContactName(TextUtils.trimToNull(r.getEmergencyContactName()));
        r.setEmergencyContactPhone(TextUtils.trimToNull(r.getEmergencyContactPhone()));
        r.setNotes(TextUtils.trimToNull(r.getNotes()));
    }

    // Update is merge-patch: trim whitespace but preserve "" so a cleared field still reaches
    // the merge logic as a clear signal (trimToNull would erase that signal).
    private static void trimStringFields(UpdateParticipantRequest r) {
        r.setChipNumber(trim(r.getChipNumber()));
        r.setFullName(trim(r.getFullName()));
        r.setGender(trim(r.getGender()));
        r.setPhoneNumber(trim(r.getPhoneNumber()));
        r.setEmail(trim(r.getEmail()));
        r.setDateOfBirth(trim(r.getDateOfBirth()));
        r.setCountry(trim(r.getCountry()));
        r.setCity(trim(r.getCity()));
        r.setRaceId(trim(r.getRaceId()));
        r.setCategoryId(trim(r.getCategoryId()));
        r.setNewBibNumber(trim(r.getNewBibNumber()));
        r.setBibCollectedAt(trim(r.getBibCollectedAt()));
        r.setEmergencyContactName(trim(r.getEmergencyContactName()));
        r.setEmergencyContactPhone(trim(r.getEmergencyContactPhone()));
        r.setNotes(trim(r.getNotes()));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Carries exactly the fields the stats counters read when they reverse a participant's
     * contribution. goodiesDistribution was missing, so every edit of a participant who had already
     * collected goodies re-counted each of them.
     */
    private ParticipantDDB snapshotForStats(ParticipantDDB p) {
        return ParticipantDDB.builder()
                .eventId(p.getEventId())
                .bibNumber(p.getBibNumber())
                .raceId(p.getRaceId())
                .categoryId(p.getCategoryId())
                .gender(p.getGender())
                .bibCollectedAt(p.getBibCollectedAt())
                .goodiesDistribution(p.getGoodiesDistribution() == null
                        ? null : new HashMap<>(p.getGoodiesDistribution()))
                .build();
    }

    @Override
    public long countParticipantsByCategoryId(Long eventId, Long categoryId) {
        log.info("Counting participants for event ID: {} and category ID: {}", eventId, categoryId);

        long count = participantRepository.countByEventAndCategory(eventId, categoryId);

        log.info("Found {} participants for category ID: {} in event ID: {}", count, categoryId, eventId);
        return count;
    }
}
