package com.timekeeper.bibexpo.event.api;

import com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB;

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
 * @param countedByValue false when the column held too many distinct values to count one by one,
 *                       in which case {@code value} is empty and {@code participants} is how many
 *                       distinct values were seen rather than how many participants
 */
public record GoodieEntitlement(String goodieName, String value, long participants,
                                boolean countedByValue) {

    /**
     * Reads one counter row as an entitlement, or returns {@code null} when the row is any other
     * kind of counter. Callers that already hold an event's rows map them with this rather than
     * asking for them again.
     */
    public static GoodieEntitlement from(EventStatsDDB row) {
        long count = row.getCount() == null ? 0L : row.getCount();
        String key = row.getStatKey();
        if (key != null && key.startsWith(EventStatsDDB.PREFIX_ENTITLED_OVERFLOW)) {
            String goodie = key.substring(EventStatsDDB.PREFIX_ENTITLED_OVERFLOW.length());
            return new GoodieEntitlement(EventStatsDDB.decodeSegment(goodie), "", count, false);
        }
        String[] parts = EventStatsDDB.entitledParts(key);
        // A counter sits at zero once every participant carrying that spelling has been deleted.
        if (parts == null || count <= 0) return null;
        return new GoodieEntitlement(parts[0], parts[1], count, true);
    }
}
