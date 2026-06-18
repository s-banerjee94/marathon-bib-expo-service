package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.model.dto.response.dashboard.EventActivityResponse;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventActivityResponse.DistributorCount;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventActivityResponse.Peak;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventActivityResponse.Point;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventActivityResponse.Rate;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventActivityResponse.Series;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventActivityResponse.Timeline;
import com.timekeeper.bibexpo.model.dynamodb.EventStatsDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.EventActivityRange;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.repository.dynamodb.EventStatsDDBRepository;
import com.timekeeper.bibexpo.service.impl.EventStatsServiceImpl;
import com.timekeeper.bibexpo.util.EventTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Computes the range-scoped activity block for the event dashboard from the
 * {@code HOUR#} and {@code DIST#} counter rows maintained in the marathon-event-stats table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventActivityService {

    private static final String INTERVAL_HOUR = "HOUR";
    private static final String UNIT_PER_HOUR = "PER_HOUR";
    private static final String SERIES_PRIMARY = "primary";
    private static final String SERIES_COMPARE = "compare";
    private static final int DEFAULT_TOP_DISTRIBUTORS = 8;
    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final EventStatsDDBRepository statsRepo;
    private final UserRepository userRepository;

    /**
     * Builds the activity block for one event and window.
     *
     * @param event the event (carries time zone and expo start/end used for bucketing and day index)
     * @param range the window to scope the activity to
     * @return the populated activity block; zeros and empty series when nothing has been collected
     */
    public EventActivityResponse computeActivity(Event event, EventActivityRange range) {
        ZoneId zone = EventTimeUtil.zoneOf(event.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        LocalDate expoStartDate = localDate(event.getEventStartDate(), zone, today);

        ParsedStats stats = parseStats(String.valueOf(event.getId()));

        List<Series> series = new ArrayList<>();
        ZonedDateTime windowStart;
        ZonedDateTime windowEnd;

        if (range == EventActivityRange.TODAY) {
            windowStart = today.atStartOfDay(zone);
            windowEnd = now;
            List<Point> primary = buildPoints(windowStart, windowEnd, zone, stats.hourCounts, expoStartDate);
            series.add(series(SERIES_PRIMARY, "Today (" + today.format(LABEL_FMT) + ")", primary));

            LocalDate yesterday = today.minusDays(1);
            List<Point> compare = yesterday.isBefore(expoStartDate)
                    ? List.of()
                    : buildPoints(yesterday.atStartOfDay(zone), yesterday.atTime(23, 0).atZone(zone),
                            zone, stats.hourCounts, expoStartDate);
            series.add(series(SERIES_COMPARE, "Yesterday (" + yesterday.format(LABEL_FMT) + ")", compare));
        } else {
            ZonedDateTime expoStartZdt = event.getEventStartDate() != null
                    ? event.getEventStartDate().atZone(zone)
                    : today.atStartOfDay(zone);
            ZonedDateTime expoEndZdt = event.getEventEndDate() != null
                    ? event.getEventEndDate().atZone(zone)
                    : now;
            ZonedDateTime nominalEnd = now.isBefore(expoEndZdt) ? now : expoEndZdt;

            // Full Expo must span ALL collection activity — including pickups recorded outside the
            // configured expo dates — so its totals can never be smaller than a single day within it.
            ZonedDateTime firstBucket = boundaryBucket(stats.hourCounts, zone, true);
            ZonedDateTime lastBucket = boundaryBucket(stats.hourCounts, zone, false);
            windowStart = (firstBucket != null && firstBucket.isBefore(expoStartZdt)) ? firstBucket : expoStartZdt;
            windowEnd = (lastBucket != null && lastBucket.isAfter(nominalEnd)) ? lastBucket : nominalEnd;

            List<Point> primary = buildPoints(windowStart, windowEnd, zone, stats.hourCounts, expoStartDate);
            series.add(series(SERIES_PRIMARY, "Full Expo", primary));
        }

        List<Point> primaryPoints = series.get(0).getPoints();
        long collected = primaryPoints.stream().mapToLong(Point::getCount).sum();

        return EventActivityResponse.builder()
                .range(range)
                .collected(collected)
                .collectedPercentOfTotal(percentOfTotal(collected, stats.total))
                .rate(buildRate(collected, windowStart, windowEnd))
                .peak(buildPeak(primaryPoints))
                .timeline(Timeline.builder().interval(INTERVAL_HOUR).series(series).build())
                .distributors(buildDistributors(stats.distByDate, range, today))
                .build();
    }

    private ParsedStats parseStats(String eventId) {
        ParsedStats parsed = new ParsedStats();
        for (EventStatsDDB row : statsRepo.queryAll(eventId)) {
            String key = row.getStatKey();
            long count = row.getCount() != null ? row.getCount() : 0L;
            if (EventStatsServiceImpl.KEY_TOTAL.equals(key)) {
                parsed.total = count;
            } else if (key.startsWith(EventStatsServiceImpl.PREFIX_HOUR)) {
                parsed.hourCounts.merge(key.substring(EventStatsServiceImpl.PREFIX_HOUR.length()), count, Long::sum);
            } else if (key.startsWith(EventStatsServiceImpl.PREFIX_DIST)) {
                String rest = key.substring(EventStatsServiceImpl.PREFIX_DIST.length());
                int sep = rest.indexOf('#');
                if (sep > 0) {
                    LocalDate date = LocalDate.parse(rest.substring(0, sep));
                    String distId = rest.substring(sep + 1);
                    parsed.distByDate.computeIfAbsent(date, d -> new HashMap<>()).merge(distId, count, Long::sum);
                }
            }
        }
        return parsed;
    }

    private static List<Point> buildPoints(ZonedDateTime startInclusive, ZonedDateTime endInclusive,
                                           ZoneId zone, Map<String, Long> hourCounts, LocalDate expoStartDate) {
        List<Point> points = new ArrayList<>();
        ZonedDateTime cursor = startInclusive.truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime end = endInclusive.truncatedTo(ChronoUnit.HOURS);
        while (!cursor.isAfter(end)) {
            LocalDate date = cursor.toLocalDate();
            String key = date + "#" + String.format("%02d", cursor.getHour());
            points.add(Point.builder()
                    .bucketStart(cursor.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .dayIndex(dayIndex(expoStartDate, date))
                    .count(hourCounts.getOrDefault(key, 0L))
                    .build());
            cursor = cursor.plusHours(1);
        }
        return points;
    }

    private static Rate buildRate(long collected, ZonedDateTime windowStart, ZonedDateTime windowEnd) {
        long minutes = Duration.between(windowStart, windowEnd).toMinutes();
        double elapsedHours = Math.max(1.0, minutes / 60.0);
        double value = Math.round(collected / elapsedHours * 10.0) / 10.0;
        return Rate.builder().value(value).unit(UNIT_PER_HOUR).build();
    }

    private static Peak buildPeak(List<Point> points) {
        return points.stream()
                .filter(p -> p.getCount() > 0)
                .max(Comparator.comparingLong(Point::getCount))
                .map(p -> Peak.builder()
                        .bucketStart(p.getBucketStart())
                        .count(p.getCount())
                        .dayIndex(p.getDayIndex())
                        .build())
                .orElse(null);
    }

    private List<DistributorCount> buildDistributors(Map<LocalDate, Map<String, Long>> distByDate,
                                                     EventActivityRange range, LocalDate today) {
        Map<String, Long> totals = new HashMap<>();
        distByDate.forEach((date, perDist) -> {
            if (inDistWindow(date, range, today)) {
                perDist.forEach((distId, count) -> totals.merge(distId, count, Long::sum));
            }
        });
        if (totals.isEmpty()) {
            return List.of();
        }

        Map<Long, String> names = resolveNames(totals.keySet());
        return totals.entrySet().stream()
                .sorted(Comparator.comparingLong(Map.Entry<String, Long>::getValue).reversed())
                .limit(DEFAULT_TOP_DISTRIBUTORS)
                .map(e -> DistributorCount.builder()
                        .distributorId(e.getKey())
                        .name(displayName(e.getKey(), names))
                        .count(e.getValue())
                        .build())
                .toList();
    }

    private static boolean inDistWindow(LocalDate date, EventActivityRange range, LocalDate today) {
        if (range == EventActivityRange.TODAY) {
            return date.equals(today);
        }
        return true; // FULL_EXPO covers all distributor activity for the event
    }

    /**
     * Earliest or latest non-empty hour bucket among the collected counters, as a zoned hour start;
     * null when there is no activity. Used to extend the Full Expo window to cover all activity.
     */
    private static ZonedDateTime boundaryBucket(Map<String, Long> hourCounts, ZoneId zone, boolean earliest) {
        ZonedDateTime boundary = null;
        for (Map.Entry<String, Long> entry : hourCounts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            ZonedDateTime bucket = bucketStart(entry.getKey(), zone);
            if (bucket == null) {
                continue;
            }
            if (boundary == null
                    || (earliest && bucket.isBefore(boundary))
                    || (!earliest && bucket.isAfter(boundary))) {
                boundary = bucket;
            }
        }
        return boundary;
    }

    private static ZonedDateTime bucketStart(String hourKey, ZoneId zone) {
        int sep = hourKey.indexOf('#');
        if (sep < 0) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(hourKey.substring(0, sep));
            int hour = Integer.parseInt(hourKey.substring(sep + 1));
            return date.atTime(hour, 0).atZone(zone);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Map<Long, String> resolveNames(Iterable<String> distIds) {
        List<Long> ids = new ArrayList<>();
        for (String id : distIds) {
            Long parsed = parseLong(id);
            if (parsed != null) {
                ids.add(parsed);
            }
        }
        Map<Long, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(ids)) {
            names.put(user.getId(), user.getFullName() != null ? user.getFullName() : user.getUsername());
        }
        return names;
    }

    private static String displayName(String distId, Map<Long, String> names) {
        Long id = parseLong(distId);
        if (id != null && names.containsKey(id)) {
            return names.get(id);
        }
        return distId;
    }

    private static Series series(String key, String label, List<Point> points) {
        return Series.builder().key(key).label(label).points(points).build();
    }

    private static double percentOfTotal(long collected, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round(collected * 1000.0 / total) / 10.0;
    }

    private static int dayIndex(LocalDate expoStartDate, LocalDate date) {
        return (int) ChronoUnit.DAYS.between(expoStartDate, date) + 1;
    }

    private static LocalDate localDate(Instant instant, ZoneId zone, LocalDate fallback) {
        return instant != null ? instant.atZone(zone).toLocalDate() : fallback;
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static final class ParsedStats {
        long total = 0L;
        final Map<String, Long> hourCounts = new HashMap<>();
        final Map<LocalDate, Map<String, Long>> distByDate = new HashMap<>();
    }
}
