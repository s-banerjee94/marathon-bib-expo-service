package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.model.dto.response.ParticipantStatisticsResponse;
import com.timekeeper.bibexpo.model.dynamodb.EventStatsDDB;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.dynamodb.EventStatsDDBRepository;
import com.timekeeper.bibexpo.repository.dynamodb.EventStatsDDBRepository.CounterDelta;
import com.timekeeper.bibexpo.repository.dynamodb.ParticipantDDBRepository;
import com.timekeeper.bibexpo.service.EventService;
import com.timekeeper.bibexpo.service.EventStatsService;
import com.timekeeper.bibexpo.service.util.DistributionConstants;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.util.EventTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventStatsServiceImpl implements EventStatsService {

    public static final String KEY_TOTAL = "TOTAL";
    public static final String KEY_BIB_COLLECTED = "BIB_COLLECTED";
    public static final String PREFIX_RACE = "RACE#";
    public static final String PREFIX_CATEGORY = "CATEGORY#";
    public static final String PREFIX_GENDER = "GENDER#";
    public static final String PREFIX_GOODIE = "GOODIE#";
    public static final String PREFIX_HOUR = "HOUR#";
    public static final String PREFIX_DIST = "DIST#";
    public static final String SUFFIX_COLLECTED = "#COLLECTED";
    public static final String SUFFIX_DISTRIBUTED = "#DISTRIBUTED";
    public static final String GENDER_M = PREFIX_GENDER + "M";
    public static final String GENDER_F = PREFIX_GENDER + "F";
    public static final String GENDER_O = PREFIX_GENDER + "O";
    private static final int PARTICIPANT_PAGE_SIZE = 100;

    private final EventStatsDDBRepository statsRepo;
    private final ParticipantDDBRepository participantRepo;
    private final EventRepository eventRepository;
    private final EventService eventService;
    private final EventAccessValidator validator;
    private final RaceCategoryNameResolver nameResolver;

    @Override
    public void onParticipantCreated(ParticipantDDB p) {
        runSafely(p.getEventId(), "onParticipantCreated", () -> {
            DeltaBuilder d = new DeltaBuilder();
            applyParticipantPresence(d, p, +1);
            statsRepo.applyDeltas(p.getEventId(), d.build());
        });
    }

    @Override
    public void onParticipantDeleted(ParticipantDDB p) {
        runSafely(p.getEventId(), "onParticipantDeleted", () -> {
            DeltaBuilder d = new DeltaBuilder();
            applyParticipantPresence(d, p, -1);
            statsRepo.applyDeltas(p.getEventId(), d.build());
        });
    }

    @Override
    public void onParticipantUpdated(ParticipantDDB before, ParticipantDDB after) {
        runSafely(after.getEventId(), "onParticipantUpdated", () -> {
            DeltaBuilder d = new DeltaBuilder();
            applyParticipantPresence(d, before, -1);
            applyParticipantPresence(d, after, +1);
            Map<String, CounterDelta> deltas = d.build();
            if (deltas.isEmpty()) return;
            statsRepo.applyDeltas(after.getEventId(), deltas);
        });
    }

    @Override
    public void onBibCollected(ParticipantDDB p, List<String> goodiesDistributed, ZoneId eventZone) {
        runSafely(p.getEventId(), "onBibCollected", () -> {
            DeltaBuilder d = new DeltaBuilder();
            d.simple(KEY_BIB_COLLECTED, +1);
            d.race(p.getRaceId(), 0, +1);
            d.category(p.getCategoryId(), 0, +1);
            if (goodiesDistributed != null) {
                for (String name : goodiesDistributed) {
                    d.simple(PREFIX_GOODIE + name + SUFFIX_DISTRIBUTED, +1);
                }
            }
            addActivityDeltas(d, p.getBibCollectedAt(), p.getBibDistributedBy(), eventZone, +1);
            statsRepo.applyDeltas(p.getEventId(), d.build());
        });
    }

    @Override
    public void onBibUndone(ParticipantDDB before, ZoneId eventZone) {
        runSafely(before.getEventId(), "onBibUndone", () -> {
            DeltaBuilder d = new DeltaBuilder();
            d.simple(KEY_BIB_COLLECTED, -1);
            d.race(before.getRaceId(), 0, -1);
            d.category(before.getCategoryId(), 0, -1);
            if (before.getGoodiesDistribution() != null) {
                for (String name : before.getGoodiesDistribution().keySet()) {
                    d.simple(PREFIX_GOODIE + name + SUFFIX_DISTRIBUTED, -1);
                }
            }
            addActivityDeltas(d, before.getBibCollectedAt(), before.getBibDistributedBy(), eventZone, -1);
            statsRepo.applyDeltas(before.getEventId(), d.build());
        });
    }

    @Override
    public void onGoodiesDistributed(ParticipantDDB p, List<String> items) {
        runSafely(p.getEventId(), "onGoodiesDistributed", () -> {
            if (items == null || items.isEmpty()) return;
            DeltaBuilder d = new DeltaBuilder();
            for (String name : items) {
                d.simple(PREFIX_GOODIE + name + SUFFIX_DISTRIBUTED, +1);
            }
            statsRepo.applyDeltas(p.getEventId(), d.build());
        });
    }

    @Override
    public void onBulkDeleted(List<ParticipantDDB> participants) {
        if (participants == null || participants.isEmpty()) return;
        String eventId = participants.get(0).getEventId();
        runSafely(eventId, "onBulkDeleted", () -> {
            DeltaBuilder d = new DeltaBuilder();
            for (ParticipantDDB p : participants) {
                applyParticipantPresence(d, p, -1);
            }
            statsRepo.applyDeltas(eventId, d.build());
        });
    }

    @Override
    public ParticipantStatisticsResponse reconcile(Long eventId, User currentUser) {
        log.info("Reconciling event stats for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException());
        validator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        ZoneId zone = EventTimeUtil.zoneOf(event.getTimezone());
        ReconcileState state = aggregateParticipants(eventId, nameResolver.forEvent(eventId), zone);
        int statRowsWritten = writeReconciledRows(eventId, state.accumulator);

        log.info("Reconciled event {}: total={} bibCollected={} statRows={}",
                eventId, state.total, state.bibCollected, statRowsWritten);

        return buildReconcileResponse(eventId, state);
    }

    private ReconcileState aggregateParticipants(Long eventId, EventNames names, ZoneId zone) {
        ReconcileState s = new ReconcileState();
        for (Page<ParticipantDDB> page : participantRepo.findPagesByEventId(eventId, PARTICIPANT_PAGE_SIZE)) {
            for (ParticipantDDB p : page.items()) {
                aggregateOne(s, p, names, zone);
            }
        }
        return s;
    }

    private static void aggregateOne(ReconcileState s, ParticipantDDB p, EventNames names, ZoneId zone) {
        s.total++;
        boolean collected = isCollected(p);
        if (collected) s.bibCollected++;

        accumulateGender(s, p);
        applyParticipantPresence(s.accumulator, p, +1);
        accumulateRace(s, p, collected, names);
        accumulateCategory(s, p, names);
        if (collected) {
            addActivityDeltas(s.accumulator, p.getBibCollectedAt(), p.getBibDistributedBy(), zone, +1);
        }
    }

    private static void accumulateGender(ReconcileState s, ParticipantDDB p) {
        String k = genderKey(p.getGender());
        if (GENDER_M.equals(k)) s.male++;
        else if (GENDER_F.equals(k)) s.female++;
        else s.other++;
    }

    private static void accumulateRace(ReconcileState s, ParticipantDDB p, boolean collected, EventNames names) {
        String raceKey = p.getRaceId() != null ? p.getRaceId() : "UNKNOWN";
        ParticipantStatisticsResponse.RaceStatistics rs = s.raceMap.computeIfAbsent(raceKey, k ->
                ParticipantStatisticsResponse.RaceStatistics.builder()
                        .raceId(p.getRaceId())
                        .raceName(names.raceName(p.getRaceId()))
                        .count(0)
                        .bibCollectedCount(0)
                        .build());
        rs.setCount(rs.getCount() + 1);
        if (collected) rs.setBibCollectedCount(rs.getBibCollectedCount() + 1);
    }

    private static void accumulateCategory(ReconcileState s, ParticipantDDB p, EventNames names) {
        String catKey = p.getCategoryId() != null ? p.getCategoryId() : "UNKNOWN";
        ParticipantStatisticsResponse.CategoryStatistics cs = s.categoryMap.computeIfAbsent(catKey, k ->
                ParticipantStatisticsResponse.CategoryStatistics.builder()
                        .categoryId(p.getCategoryId())
                        .categoryName(names.categoryName(p.getCategoryId()))
                        .count(0)
                        .build());
        cs.setCount(cs.getCount() + 1);
    }

    private int writeReconciledRows(Long eventId, DeltaBuilder accumulator) {
        String eventIdStr = eventId.toString();
        statsRepo.deleteAllByEventId(eventIdStr);
        List<EventStatsDDB> rows = toRows(eventIdStr, accumulator);
        statsRepo.putAll(rows);
        return rows.size();
    }

    private static ParticipantStatisticsResponse buildReconcileResponse(Long eventId, ReconcileState s) {
        return ParticipantStatisticsResponse.builder()
                .eventId(eventId)
                .totalParticipants(s.total)
                .bibCollectedCount(s.bibCollected)
                .pendingCount(s.total - s.bibCollected)
                .raceBreakdown(new ArrayList<>(s.raceMap.values()))
                .categoryBreakdown(new ArrayList<>(s.categoryMap.values()))
                .genderBreakdown(ParticipantStatisticsResponse.GenderStatistics.builder()
                        .male(s.male)
                        .female(s.female)
                        .other(s.other)
                        .build())
                .build();
    }

    private static final class ReconcileState {
        int total = 0;
        int bibCollected = 0;
        int male = 0;
        int female = 0;
        int other = 0;
        final Map<String, ParticipantStatisticsResponse.RaceStatistics> raceMap = new LinkedHashMap<>();
        final Map<String, ParticipantStatisticsResponse.CategoryStatistics> categoryMap = new LinkedHashMap<>();
        final DeltaBuilder accumulator = new DeltaBuilder();
    }

    private static void applyParticipantPresence(DeltaBuilder d, ParticipantDDB p, long sign) {
        boolean collected = isCollected(p);
        long collectedSign = collected ? sign : 0;

        d.simple(KEY_TOTAL, sign);
        d.race(p.getRaceId(), sign, collectedSign);
        d.category(p.getCategoryId(), sign, collectedSign);
        d.simple(genderKey(p.getGender()), sign);

        if (collected) {
            d.simple(KEY_BIB_COLLECTED, sign);
        }
        if (p.getGoodiesDistribution() != null) {
            for (String name : p.getGoodiesDistribution().keySet()) {
                d.simple(PREFIX_GOODIE + name + SUFFIX_DISTRIBUTED, sign);
            }
        }
    }

    private static List<EventStatsDDB> toRows(String eventIdStr, DeltaBuilder accumulator) {
        Map<String, CounterDelta> built = accumulator.build();
        String now = Instant.now().toString();
        List<EventStatsDDB> rows = new ArrayList<>(built.size());
        built.forEach((k, v) -> rows.add(EventStatsDDB.builder()
                .eventId(eventIdStr)
                .statKey(k)
                .count(v.delta())
                .updatedAt(now)
                .build()));
        return rows;
    }

    /**
     * Bumps the range-scoped activity counters for one bib collection: an hourly bucket
     * (HOUR#&lt;localDate&gt;#&lt;hh&gt;) and a per-distributor/day bucket (DIST#&lt;localDate&gt;#&lt;id&gt;),
     * both bucketed in the event's time zone. No-op when the collection time is absent.
     */
    private static void addActivityDeltas(DeltaBuilder d, String collectedAtIso,
                                          String bibDistributedBy, ZoneId zone, long sign) {
        if (collectedAtIso == null || collectedAtIso.isBlank() || zone == null) {
            return;
        }
        ZonedDateTime local = Instant.parse(collectedAtIso).atZone(zone);
        String date = local.toLocalDate().toString();
        d.simple(PREFIX_HOUR + date + "#" + String.format("%02d", local.getHour()), sign);
        String distId = distributorId(bibDistributedBy);
        if (distId != null) {
            d.simple(PREFIX_DIST + date + "#" + distId, sign);
        }
    }

    private static String distributorId(String bibDistributedBy) {
        if (bibDistributedBy == null || bibDistributedBy.isBlank()) {
            return null;
        }
        int idx = bibDistributedBy.indexOf(DistributionConstants.DISTRIBUTOR_SEPARATOR);
        return idx > 0 ? bibDistributedBy.substring(0, idx) : bibDistributedBy;
    }

    private static boolean isCollected(ParticipantDDB p) {
        String at = p.getBibCollectedAt();
        return at != null && !at.isBlank();
    }

    private static String genderKey(String gender) {
        if ("M".equalsIgnoreCase(gender)) return GENDER_M;
        if ("F".equalsIgnoreCase(gender)) return GENDER_F;
        return GENDER_O;
    }

    private void runSafely(String eventId, String operation, Runnable body) {
        try {
            body.run();
        } catch (Exception ex) {
            log.error("Event stats update failed eventId={} op={} err={}",
                    eventId, operation, ex.getMessage(), ex);
        }
    }

    private static final class DeltaBuilder {
        private final Map<String, Long> counts = new HashMap<>();

        void simple(String key, long delta) {
            if (delta == 0) return;
            counts.merge(key, delta, Long::sum);
        }

        void race(String raceId, long totalDelta, long collectedDelta) {
            dimension(PREFIX_RACE, raceId, totalDelta, collectedDelta);
        }

        void category(String categoryId, long totalDelta, long collectedDelta) {
            dimension(PREFIX_CATEGORY, categoryId, totalDelta, collectedDelta);
        }

        private void dimension(String prefix, String id, long totalDelta, long collectedDelta) {
            if (id == null) return;
            String k = prefix + id;
            if (totalDelta != 0) {
                counts.merge(k, totalDelta, Long::sum);
            }
            if (collectedDelta != 0) {
                counts.merge(k + SUFFIX_COLLECTED, collectedDelta, Long::sum);
            }
        }

        Map<String, CounterDelta> build() {
            Map<String, CounterDelta> result = new HashMap<>();
            counts.forEach((k, v) -> {
                if (v == 0L) return;
                result.put(k, new CounterDelta(v));
            });
            return result;
        }
    }
}
