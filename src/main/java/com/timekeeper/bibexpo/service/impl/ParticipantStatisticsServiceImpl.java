package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.model.dto.response.ParticipantStatisticsResponse;
import com.timekeeper.bibexpo.model.dynamodb.EventStatsDDB;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.dynamodb.EventStatsDDBRepository;
import com.timekeeper.bibexpo.service.ParticipantStatisticsService;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.service.validator.ParticipantAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantStatisticsServiceImpl implements ParticipantStatisticsService {

    private final ParticipantAccessGuard accessGuard;
    private final EventStatsDDBRepository eventStatsRepo;
    private final RaceCategoryNameResolver nameResolver;

    @Override
    public ParticipantStatisticsResponse getParticipantStatistics(Long eventId, User currentUser) {
        log.info("Getting participant statistics for event ID: {} by user: {}", eventId, currentUser.getUsername());

        accessGuard.forRead(eventId, currentUser);

        List<EventStatsDDB> rows = eventStatsRepo.queryAll(eventId.toString());
        if (rows.isEmpty()) {
            log.warn("No stats counters found for event {} — counters are built on participant writes "
                    + "and rebuilt by EventStatsService.reconcile after a batch import", eventId);
            return emptyStatistics(eventId);
        }

        return buildStatisticsFromRows(eventId, rows, nameResolver.forEvent(eventId));
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
                case EventStatsServiceImpl.KEY_TOTAL -> total = (int) count;
                case EventStatsServiceImpl.KEY_BIB_COLLECTED -> bibCollected = (int) count;
                case EventStatsServiceImpl.GENDER_M -> male = (int) count;
                case EventStatsServiceImpl.GENDER_F -> female = (int) count;
                case EventStatsServiceImpl.GENDER_O -> other = (int) count;
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
                .genderBreakdown(ParticipantStatisticsResponse.GenderStatistics.builder()
                        .male(male)
                        .female(female)
                        .other(other)
                        .build())
                .build();
    }

    private void applyDimensionRow(
            String key, long count,
            Map<String, ParticipantStatisticsResponse.RaceStatistics> raceMap,
            Map<String, ParticipantStatisticsResponse.CategoryStatistics> categoryMap,
            EventNames names) {

        if (key.startsWith(EventStatsServiceImpl.PREFIX_RACE)) {
            applyRaceRow(key, count, raceMap, names);
        } else if (key.startsWith(EventStatsServiceImpl.PREFIX_CATEGORY)) {
            applyCategoryRow(key, count, categoryMap, names);
        }
    }

    private void applyRaceRow(
            String key, long count,
            Map<String, ParticipantStatisticsResponse.RaceStatistics> raceMap,
            EventNames names) {

        boolean collected = key.endsWith(EventStatsServiceImpl.SUFFIX_COLLECTED);
        String raceId = collected
                ? key.substring(EventStatsServiceImpl.PREFIX_RACE.length(),
                        key.length() - EventStatsServiceImpl.SUFFIX_COLLECTED.length())
                : key.substring(EventStatsServiceImpl.PREFIX_RACE.length());

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

        boolean collected = key.endsWith(EventStatsServiceImpl.SUFFIX_COLLECTED);
        String categoryId = collected
                ? key.substring(EventStatsServiceImpl.PREFIX_CATEGORY.length(),
                        key.length() - EventStatsServiceImpl.SUFFIX_COLLECTED.length())
                : key.substring(EventStatsServiceImpl.PREFIX_CATEGORY.length());

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
                .genderBreakdown(ParticipantStatisticsResponse.GenderStatistics.builder()
                        .male(0).female(0).other(0).build())
                .build();
    }
}
