package com.timekeeper.bibexpo.participant.service.impl;

import com.timekeeper.bibexpo.event.api.EventStatsQuery;
import com.timekeeper.bibexpo.event.api.GoodieEntitlement;
import com.timekeeper.bibexpo.event.api.EventStatsRebuild;
import com.timekeeper.bibexpo.event.api.EventStatsRecorder;
import com.timekeeper.bibexpo.event.api.ParticipantCounters;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB;
import com.timekeeper.bibexpo.participant.model.dto.response.ParticipantStatisticsResponse;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.participant.repository.ParticipantDDBRepository;
import com.timekeeper.bibexpo.participant.service.ParticipantStatisticsService;
import com.timekeeper.bibexpo.participant.service.util.ParticipantCountersMapper;
import com.timekeeper.bibexpo.participant.service.validator.ParticipantAccessGuard;
import com.timekeeper.bibexpo.event.api.EventNames;
import com.timekeeper.bibexpo.event.api.RaceCategoryNameQuery;
import com.timekeeper.bibexpo.shared.util.EventTimeUtil;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantStatisticsServiceImpl implements ParticipantStatisticsService {

    private static final int PARTICIPANT_PAGE_SIZE = 100;

    private final ParticipantAccessGuard accessGuard;
    private final ParticipantDDBRepository participantRepository;
    private final EventStatsQuery eventStatsQuery;
    private final EventStatsRecorder eventStatsRecorder;
    private final RaceCategoryNameQuery nameResolver;

    @Override
    public ParticipantStatisticsResponse getParticipantStatistics(Long eventId, User currentUser) {
        log.info("Getting participant statistics for event ID: {} by user: {}", eventId, currentUser.getUsername());

        accessGuard.forRead(eventId, currentUser);

        List<EventStatsDDB> rows = eventStatsQuery.counters(eventId);
        if (rows.isEmpty()) {
            log.warn("No stats counters found for event {} — counters are built on participant writes "
                    + "and rebuilt by reconcile after a batch import", eventId);
            return emptyStatistics(eventId);
        }

        return buildStatisticsFromRows(eventId, rows, nameResolver.forEvent(eventId));
    }

    @Override
    public void reconcile(Long eventId, User currentUser) {
        log.info("Reconciling event stats for event ID: {} by user: {}", eventId, currentUser.getUsername());
        rebuild(accessGuard.forRead(eventId, currentUser));
    }

    @Override
    public void rebuild(Event event) {
        ZoneId zone = EventTimeUtil.zoneOf(event.getTimezone());

        EventStatsRebuild result = eventStatsRecorder.rebuild(event.getId(), zone, roster(event.getId()));

        log.info("Reconciled event {}: total={} bibCollected={} statRows={}",
                event.getId(), result.participants(), result.bibCollected(), result.counterRows());
    }

    private Stream<ParticipantCounters> roster(Long eventId) {
        Iterable<Page<ParticipantDDB>> pages = participantRepository.findPagesByEventId(eventId, PARTICIPANT_PAGE_SIZE);
        return StreamSupport.stream(pages.spliterator(), false)
                .flatMap(page -> page.items().stream())
                .map(ParticipantCountersMapper::of);
    }

    private ParticipantStatisticsResponse buildStatisticsFromRows(Long eventId, List<EventStatsDDB> rows, EventNames names) {
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
                case EventStatsDDB.KEY_TOTAL -> total = (int) count;
                case EventStatsDDB.KEY_BIB_COLLECTED -> bibCollected = (int) count;
                case EventStatsDDB.GENDER_M -> male = (int) count;
                case EventStatsDDB.GENDER_F -> female = (int) count;
                case EventStatsDDB.GENDER_O -> other = (int) count;
                default -> applyDimensionRow(key, count, raceMap, categoryMap, names);
            }
        }

        log.info("Loaded statistics for event {} from counters: total={} bibCollected={}",
                eventId, total, bibCollected);
        warnOnUnresolvedIds(eventId, raceMap, categoryMap, names);

        return ParticipantStatisticsResponse.builder()
                .eventId(eventId)
                .totalParticipants(total)
                .bibCollectedCount(bibCollected)
                .pendingCount(Math.max(0, total - bibCollected))
                .raceBreakdown(new ArrayList<>(raceMap.values()))
                .categoryBreakdown(new ArrayList<>(categoryMap.values()))
                .goodiesBreakdown(goodiesBreakdown(rows))
                .genderBreakdown(ParticipantStatisticsResponse.GenderStatistics.builder()
                        .male(male)
                        .female(female)
                        .other(other)
                        .build())
                .build();
    }

    /**
     * Reads the entitlement counters out of the rows already fetched, so the roster's demand costs
     * this call nothing beyond the query it was going to make anyway.
     */
    private static List<ParticipantStatisticsResponse.GoodieDemand> goodiesBreakdown(
            List<EventStatsDDB> rows) {
        return GoodieEntitlement.fromRows(rows).stream()
                .map(e -> ParticipantStatisticsResponse.GoodieDemand.builder()
                        .goodieName(e.goodieName())
                        .value(e.value())
                        .participants(e.participants())
                        .countedByValue(e.countedByValue())
                        .build())
                .toList();
    }

    private void applyDimensionRow(
            String key, long count,
            Map<String, ParticipantStatisticsResponse.RaceStatistics> raceMap,
            Map<String, ParticipantStatisticsResponse.CategoryStatistics> categoryMap,
            EventNames names) {

        if (key.startsWith(EventStatsDDB.PREFIX_RACE)) {
            applyRaceRow(key, count, raceMap, names);
        } else if (key.startsWith(EventStatsDDB.PREFIX_CATEGORY)) {
            applyCategoryRow(key, count, categoryMap, names);
        }
    }

    private void applyRaceRow(
            String key, long count,
            Map<String, ParticipantStatisticsResponse.RaceStatistics> raceMap,
            EventNames names) {

        boolean collected = key.endsWith(EventStatsDDB.SUFFIX_COLLECTED);
        String raceId = collected
                ? key.substring(EventStatsDDB.PREFIX_RACE.length(),
                        key.length() - EventStatsDDB.SUFFIX_COLLECTED.length())
                : key.substring(EventStatsDDB.PREFIX_RACE.length());

        ParticipantStatisticsResponse.RaceStatistics rs = raceMap.computeIfAbsent(raceId,
                k -> ParticipantStatisticsResponse.RaceStatistics.builder()
                        .raceId(k)
                        .raceName(names.raceLabel(k))
                        .count(0)
                        .bibCollectedCount(0)
                        .build());

        if (collected) {
            rs.setBibCollectedCount((int) count);
        } else {
            rs.setCount((int) count);
        }
    }

    private void applyCategoryRow(
            String key, long count,
            Map<String, ParticipantStatisticsResponse.CategoryStatistics> categoryMap,
            EventNames names) {

        boolean collected = key.endsWith(EventStatsDDB.SUFFIX_COLLECTED);
        String categoryId = collected
                ? key.substring(EventStatsDDB.PREFIX_CATEGORY.length(),
                        key.length() - EventStatsDDB.SUFFIX_COLLECTED.length())
                : key.substring(EventStatsDDB.PREFIX_CATEGORY.length());

        ParticipantStatisticsResponse.CategoryStatistics cs = categoryMap.computeIfAbsent(categoryId,
                k -> ParticipantStatisticsResponse.CategoryStatistics.builder()
                        .categoryId(k)
                        .categoryName(names.categoryLabel(k))
                        .count(0)
                        .bibCollectedCount(0)
                        .build());

        if (collected) {
            cs.setBibCollectedCount((int) count);
        } else {
            cs.setCount((int) count);
        }
    }

    /**
     * Counter keys are the only record of which races/categories participants point at, so an id that
     * no longer resolves means the relational rows and the participant store have drifted apart. Log
     * it here — the rollup itself degrades to a placeholder label rather than failing.
     */
    private void warnOnUnresolvedIds(
            Long eventId,
            Map<String, ParticipantStatisticsResponse.RaceStatistics> raceMap,
            Map<String, ParticipantStatisticsResponse.CategoryStatistics> categoryMap,
            EventNames names) {

        List<String> races = raceMap.keySet().stream().filter(id -> !names.hasRace(id)).toList();
        List<String> categories = categoryMap.keySet().stream().filter(id -> !names.hasCategory(id)).toList();
        if (races.isEmpty() && categories.isEmpty()) {
            return;
        }

        long affected = categoryMap.entrySet().stream()
                .filter(e -> !names.hasCategory(e.getKey()))
                .mapToLong(e -> e.getValue().getCount() != null ? e.getValue().getCount() : 0)
                .sum();

        log.warn("Event {} has participants pointing at races/categories that no longer exist — "
                        + "unresolved races: {}, unresolved categories: {} ({} participant(s) unlabelled). "
                        + "Reconcile will not repair this; the participant records must be re-imported or "
                        + "reassigned to live races/categories.",
                eventId, races, categories, affected);
    }

    private ParticipantStatisticsResponse emptyStatistics(Long eventId) {
        return ParticipantStatisticsResponse.builder()
                .eventId(eventId)
                .totalParticipants(0)
                .bibCollectedCount(0)
                .pendingCount(0)
                .raceBreakdown(new ArrayList<>())
                .categoryBreakdown(new ArrayList<>())
                .goodiesBreakdown(List.of())
                .genderBreakdown(ParticipantStatisticsResponse.GenderStatistics.builder()
                        .male(0).female(0).other(0).build())
                .build();
    }
}
