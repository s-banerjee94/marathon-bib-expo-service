package com.timekeeper.bibexpo.event.api;

import com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How many of an event's participants were promised one goody spelled one particular way.
 *
 * <p>The value is the imported cell text as it stands, not something the catalogue recognises:
 * a roster may say {@code M} where the item's sizes read {@code 38}. Turning one into the other is
 * the reader's business, and this is the demand side it starts from.
 *
 * @param goodieName   the goodies column heading the import stored
 * @param value        the cell value, exactly as imported
 * @param participants how many participants carry that value
 * @param handedOut    how many of those participants have already been handed the goody; unreliable for
 *                     an event whose counters were last rebuilt before this was counted, until it is
 *                     reconciled
 * @param countedByValue false when the column held too many distinct values to count one by one,
 *                       in which case {@code value} is empty and {@code participants} is how many
 *                       distinct values were seen rather than how many participants
 */
public record GoodieEntitlement(String goodieName, String value, long participants, long handedOut,
                                boolean countedByValue) {

    /**
     * Reads the entitlements out of an event's counter rows, each with how many were already handed
     * out, sorted by goody and value. Callers that already hold the rows map them with this rather than
     * asking for them again.
     *
     * @param rows every counter row of one event
     * @return the entitlements; rows of any other kind are skipped
     */
    public static List<GoodieEntitlement> fromRows(List<EventStatsDDB> rows) {
        Map<String, Long> handed = new HashMap<>();
        for (EventStatsDDB row : rows) {
            String entitledKey = EventStatsDDB.entitledKeyOfHanded(row.getStatKey());
            if (entitledKey != null) {
                handed.put(entitledKey, countOf(row));
            }
        }
        return rows.stream()
                .map(row -> from(row, handed.getOrDefault(row.getStatKey(), 0L)))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(GoodieEntitlement::goodieName)
                        .thenComparing(GoodieEntitlement::value))
                .toList();
    }

    private static GoodieEntitlement from(EventStatsDDB row, long handedOut) {
        long count = countOf(row);
        String key = row.getStatKey();
        if (key != null && key.startsWith(EventStatsDDB.PREFIX_ENTITLED_OVERFLOW)) {
            String goodie = key.substring(EventStatsDDB.PREFIX_ENTITLED_OVERFLOW.length());
            return new GoodieEntitlement(EventStatsDDB.decodeSegment(goodie), "", count, 0, false);
        }
        String[] parts = EventStatsDDB.entitledParts(key);
        // A counter sits at zero once every participant carrying that spelling has been deleted.
        if (parts == null || count <= 0) return null;
        return new GoodieEntitlement(parts[0], parts[1], count, Math.max(0, Math.min(handedOut, count)), true);
    }

    private static long countOf(EventStatsDDB row) {
        return row.getCount() == null ? 0L : row.getCount();
    }
}
