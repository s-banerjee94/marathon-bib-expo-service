package com.timekeeper.bibexpo.util;

import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Resolves the time zone used to bucket event-local activity (collection hours, expo days).
 */
public final class EventTimeUtil {

    private EventTimeUtil() {
    }

    /**
     * Resolves an event's IANA time zone, falling back to UTC when the event carries none.
     *
     * @param timezone the event's {@code timezone} (e.g. {@code Asia/Kolkata}); may be {@code null}
     * @return the resolved zone, or {@link ZoneOffset#UTC} when {@code timezone} is null
     */
    public static ZoneId zoneOf(String timezone) {
        return timezone != null ? ZoneId.of(timezone) : ZoneOffset.UTC;
    }
}
