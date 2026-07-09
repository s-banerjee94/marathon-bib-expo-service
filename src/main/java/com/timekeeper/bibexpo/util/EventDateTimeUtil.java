package com.timekeeper.bibexpo.util;

import com.timekeeper.bibexpo.exception.InvalidUserDataException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Converts between a wall-clock date/time in an event's local timezone and the UTC {@link Instant}
 * persisted in the database. Events and races both store an absolute instant but are entered and
 * displayed as a local date and time, so the conversion lives here once instead of being copied
 * into each service and response.
 */
public final class EventDateTimeUtil {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private EventDateTimeUtil() {
    }

    /** Validate an IANA timezone id (e.g. {@code Asia/Kolkata}) and return its {@link ZoneId}. */
    public static ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new InvalidUserDataException(
                    "Invalid timezone. Use a valid IANA timezone ID such as 'Asia/Kolkata' or 'Europe/London'.");
        }
    }

    /** Combine a {@code yyyy-MM-dd} date and {@code HH:mm} time in the given zone into a UTC instant. */
    public static Instant toInstant(String date, String time, ZoneId zone) {
        try {
            return ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone).toInstant();
        } catch (DateTimeParseException e) {
            throw new InvalidUserDataException("Invalid date or time format. Use yyyy-MM-dd and HH:mm.");
        }
    }

    /** The local date ({@code yyyy-MM-dd}) of an instant as seen in the given zone. */
    public static String dateOf(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toLocalDate().toString();
    }

    /** The local time ({@code HH:mm}) of an instant as seen in the given zone. */
    public static String timeOf(Instant instant, ZoneId zone) {
        return instant.atZone(zone).format(TIME);
    }
}
