package com.timekeeper.bibexpo.event.stats.service.impl;

import com.timekeeper.bibexpo.event.api.EventStatsRebuild;
import com.timekeeper.bibexpo.event.api.EventStatsRecorder;
import com.timekeeper.bibexpo.event.api.ParticipantCounters;
import com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB;
import com.timekeeper.bibexpo.event.stats.repository.EventStatsDDBRepository.CounterDelta;
import com.timekeeper.bibexpo.event.stats.repository.EventStatsDDBRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.GENDER_F;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.GENDER_M;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.GENDER_O;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.KEY_BIB_COLLECTED;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.KEY_GOODIES_PENDING;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.KEY_TOTAL;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.PREFIX_CATEGORY;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.PREFIX_DIST;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.PREFIX_ENTITLED_OVERFLOW;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.PREFIX_GOODIE;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.PREFIX_HOUR;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.PREFIX_RACE;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.SUFFIX_COLLECTED;
import static com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB.SUFFIX_DISTRIBUTED;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventStatsRecorderImpl implements EventStatsRecorder {

    // A goody has a handful of spellings. A column with more than this many distinct values is
    // not a goody at all -- someone marked the wrong column on the import screen -- so its value
    // rows are dropped for one marker row rather than let a roster's worth of them into a table
    // every dashboard read pulls whole.
    private static final int MAX_VALUES_PER_GOODIE = 200;

    private final EventStatsDDBRepository statsRepo;

    @Override
    public void onParticipantCreated(ParticipantCounters p) {
        runSafely(p.eventId(), "onParticipantCreated", () -> {
            DeltaBuilder d = new DeltaBuilder();
            applyParticipantPresence(d, p, +1);
            statsRepo.applyDeltas(p.eventId(), d.build());
        });
    }

    @Override
    public void onParticipantDeleted(ParticipantCounters p) {
        runSafely(p.eventId(), "onParticipantDeleted", () -> {
            DeltaBuilder d = new DeltaBuilder();
            applyParticipantPresence(d, p, -1);
            statsRepo.applyDeltas(p.eventId(), d.build());
        });
    }

    @Override
    public void onParticipantUpdated(ParticipantCounters before, ParticipantCounters after) {
        runSafely(after.eventId(), "onParticipantUpdated", () -> {
            DeltaBuilder d = new DeltaBuilder();
            applyParticipantPresence(d, before, -1);
            applyParticipantPresence(d, after, +1);
            Map<String, CounterDelta> deltas = d.build();
            if (deltas.isEmpty()) return;
            statsRepo.applyDeltas(after.eventId(), deltas);
        });
    }

    @Override
    public void onBibCollected(ParticipantCounters p, List<String> goodiesDistributed, ZoneId eventZone) {
        runSafely(p.eventId(), "onBibCollected", () -> {
            DeltaBuilder d = new DeltaBuilder();
            d.simple(KEY_BIB_COLLECTED, +1);
            d.race(p.raceId(), 0, +1);
            d.category(p.categoryId(), 0, +1);
            if (owesGoodies(p, p.goodiesCollected())) {
                d.simple(KEY_GOODIES_PENDING, +1);
            }
            if (goodiesDistributed != null) {
                for (String name : goodiesDistributed) {
                    d.simple(PREFIX_GOODIE + name + SUFFIX_DISTRIBUTED, +1);
                }
            }
            addActivityDeltas(d, p, eventZone, +1);
            statsRepo.applyDeltas(p.eventId(), d.build());
        });
    }

    @Override
    public void onBibUndone(ParticipantCounters before, ZoneId eventZone) {
        runSafely(before.eventId(), "onBibUndone", () -> {
            DeltaBuilder d = new DeltaBuilder();
            d.simple(KEY_BIB_COLLECTED, -1);
            d.race(before.raceId(), 0, -1);
            d.category(before.categoryId(), 0, -1);
            if (owesGoodies(before, before.goodiesCollected())) {
                d.simple(KEY_GOODIES_PENDING, -1);
            }
            for (String name : before.goodiesCollected()) {
                d.simple(PREFIX_GOODIE + name + SUFFIX_DISTRIBUTED, -1);
            }
            addActivityDeltas(d, before, eventZone, -1);
            statsRepo.applyDeltas(before.eventId(), d.build());
        });
    }

    @Override
    public void onGoodiesDistributed(ParticipantCounters p, List<String> items) {
        runSafely(p.eventId(), "onGoodiesDistributed", () -> {
            if (items == null || items.isEmpty()) return;
            DeltaBuilder d = new DeltaBuilder();
            for (String name : items) {
                d.simple(PREFIX_GOODIE + name + SUFFIX_DISTRIBUTED, +1);
            }
            // A hand-out only ever settles what was owed, so the only move is off the pending count.
            Set<String> handedBefore = new HashSet<>(p.goodiesCollected());
            handedBefore.removeAll(items);
            if (owesGoodies(p, handedBefore) && !owesGoodies(p, p.goodiesCollected())) {
                d.simple(KEY_GOODIES_PENDING, -1);
            }
            statsRepo.applyDeltas(p.eventId(), d.build());
        });
    }

    @Override
    public void onBulkDeleted(List<ParticipantCounters> participants) {
        if (participants == null || participants.isEmpty()) return;
        String eventId = participants.get(0).eventId();
        runSafely(eventId, "onBulkDeleted", () -> {
            DeltaBuilder d = new DeltaBuilder();
            for (ParticipantCounters p : participants) {
                applyParticipantPresence(d, p, -1);
            }
            statsRepo.applyDeltas(eventId, d.build());
        });
    }

    @Override
    public void onAllDeleted(Long eventId) {
        runSafely(eventId.toString(), "onAllDeleted",
                () -> statsRepo.deleteAllByEventId(eventId.toString()));
    }

    @Override
    public EventStatsRebuild rebuild(Long eventId, ZoneId eventZone, Stream<ParticipantCounters> roster) {
        RebuildState state = new RebuildState();
        roster.forEach(p -> aggregateOne(state, p, eventZone));

        String eventIdStr = eventId.toString();
        statsRepo.deleteAllByEventId(eventIdStr);
        List<EventStatsDDB> rows = toRows(eventIdStr, state.accumulator);
        statsRepo.putAll(rows);

        return new EventStatsRebuild(state.total, state.bibCollected, rows.size());
    }

    private static void aggregateOne(RebuildState s, ParticipantCounters p, ZoneId zone) {
        s.total++;
        if (p.bibCollected()) {
            s.bibCollected++;
            addActivityDeltas(s.accumulator, p, zone, +1);
        }
        applyParticipantPresence(s.accumulator, p, +1);
    }

    /** Running totals for the rebuild result; the counter rows themselves come from the accumulator. */
    private static final class RebuildState {
        int total = 0;
        int bibCollected = 0;
        final DeltaBuilder accumulator = new DeltaBuilder();
    }

    private static void applyParticipantPresence(DeltaBuilder d, ParticipantCounters p, long sign) {
        boolean collected = p.bibCollected();
        long collectedSign = collected ? sign : 0;

        d.simple(KEY_TOTAL, sign);
        d.race(p.raceId(), sign, collectedSign);
        d.category(p.categoryId(), sign, collectedSign);
        d.simple(genderKey(p.gender()), sign);

        if (collected) {
            d.simple(KEY_BIB_COLLECTED, sign);
        }
        if (owesGoodies(p, p.goodiesCollected())) {
            d.simple(KEY_GOODIES_PENDING, sign);
        }
        for (String name : p.goodiesCollected()) {
            d.simple(PREFIX_GOODIE + name + SUFFIX_DISTRIBUTED, sign);
        }
        p.goodiesEntitled().forEach((name, value) ->
                d.simple(EventStatsDDB.entitledKey(name, value), sign));
    }

    // The counter's goodies list rule: a collected bib with at least one goody of the participant's own not
    // yet handed over.
    private static boolean owesGoodies(ParticipantCounters p, Set<String> handed) {
        return p.bibCollected() && !handed.containsAll(p.goodiesEntitled().keySet());
    }

    private static List<EventStatsDDB> toRows(String eventIdStr, DeltaBuilder accumulator) {
        Map<String, CounterDelta> built = capEntitlementValues(accumulator.build());
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
     * Replaces the entitlement rows of any goody with more distinct values than a goody plausibly
     * has by a single {@code ENTITLEDMANY#} row carrying how many were seen. Only the rebuild runs
     * this: it is the one path that sees a whole roster at once, and so the only one where a
     * mis-marked column can arrive as thousands of rows.
     */
    private static Map<String, CounterDelta> capEntitlementValues(Map<String, CounterDelta> built) {
        Map<String, Integer> valuesPerGoodie = new HashMap<>();
        built.keySet().forEach(key -> {
            String goodie = EventStatsDDB.entitledGoodieSegment(key);
            if (goodie != null) valuesPerGoodie.merge(goodie, 1, Integer::sum);
        });
        Set<String> overflowing = valuesPerGoodie.entrySet().stream()
                .filter(e -> e.getValue() > MAX_VALUES_PER_GOODIE)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (overflowing.isEmpty()) return built;

        Map<String, CounterDelta> kept = new HashMap<>(built);
        kept.keySet().removeIf(key -> overflowing.contains(EventStatsDDB.entitledGoodieSegment(key)));
        overflowing.forEach(goodie -> {
            kept.put(PREFIX_ENTITLED_OVERFLOW + goodie, new CounterDelta(valuesPerGoodie.get(goodie)));
            log.warn("Goody column '{}' has {} distinct values and was not counted by value; "
                    + "the wrong column was most likely marked as goodies",
                    EventStatsDDB.decodeSegment(goodie), valuesPerGoodie.get(goodie));
        });
        return kept;
    }

    /**
     * Bumps the range-scoped activity counters for one bib collection: an hourly bucket
     * (HOUR#&lt;localDate&gt;#&lt;hh&gt;) and a per-distributor/day bucket (DIST#&lt;localDate&gt;#&lt;id&gt;),
     * both bucketed in the event's time zone. No-op when the collection time is absent.
     */
    private static void addActivityDeltas(DeltaBuilder d, ParticipantCounters p, ZoneId zone, long sign) {
        if (!p.bibCollected() || zone == null) {
            return;
        }
        ZonedDateTime local = Instant.parse(p.bibCollectedAt()).atZone(zone);
        String date = local.toLocalDate().toString();
        d.simple(PREFIX_HOUR + date + "#" + String.format("%02d", local.getHour()), sign);
        if (p.bibDistributorId() != null) {
            d.simple(PREFIX_DIST + date + "#" + p.bibDistributorId(), sign);
        }
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
