package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.model.dto.response.ParticipantStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventDashboardResponse;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventDashboardResponse.CategoryStat;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventDashboardResponse.EventContext;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventDashboardResponse.GenderBreakdown;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventDashboardResponse.ParticipantTotals;
import com.timekeeper.bibexpo.model.dto.response.dashboard.EventDashboardResponse.RaceStat;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.EventActivityRange;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.ParticipantStatisticsService;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.util.EventTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Assembles the event dashboard rollup: the event-wide participant statistics (reused from
 * {@link ParticipantStatisticsService}), the range-scoped activity block, and event context,
 * under one {@code refreshedAt}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventDashboardService {

    private final ParticipantStatisticsService participantStatisticsService;
    private final EventActivityService eventActivityService;
    private final EventRepository eventRepository;
    private final RaceCategoryNameResolver nameResolver;

    /**
     * Loads the full dashboard for one event and window. Access control and event existence are
     * enforced by the reused participant-statistics read.
     *
     * @param eventId     the event
     * @param range       the activity window
     * @param currentUser the authenticated caller
     * @return the assembled rollup
     */
    public EventDashboardResponse loadDashboard(Long eventId, EventActivityRange range, User currentUser) {
        log.info("Loading event dashboard — event: {}, range: {}, user: {}",
                eventId, range, currentUser.getUsername());

        ParticipantStatisticsResponse stats = participantStatisticsService.getParticipantStatistics(eventId, currentUser);
        Event event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
        EventNames names = nameResolver.forEvent(eventId);

        ZoneId zone = EventTimeUtil.zoneOf(event.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(zone);

        return EventDashboardResponse.builder()
                .refreshedAt(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .range(range)
                .event(buildContext(event, zone, now))
                .participants(buildParticipants(stats))
                .gender(buildGender(stats))
                .races(buildRaces(stats))
                .categories(buildCategories(stats, names))
                .activity(eventActivityService.computeActivity(event, range))
                .build();
    }

    private static EventContext buildContext(Event event, ZoneId zone, ZonedDateTime now) {
        LocalDate startDate = localDate(event.getEventStartDate(), zone);
        LocalDate endDate = localDate(event.getEventEndDate(), zone);
        int dayCount = (startDate != null && endDate != null)
                ? (int) ChronoUnit.DAYS.between(startDate, endDate) + 1 : 0;

        Integer currentDayIndex = null;
        if (startDate != null && endDate != null) {
            LocalDate todayDate = now.toLocalDate();
            if (!todayDate.isBefore(startDate) && !todayDate.isAfter(endDate)) {
                currentDayIndex = (int) ChronoUnit.DAYS.between(startDate, todayDate) + 1;
            }
        }

        return EventContext.builder()
                .eventId(event.getId())
                .timezone(event.getTimezone())
                .expoStart(format(event.getEventStartDate(), zone))
                .expoEnd(format(event.getEventEndDate(), zone))
                .dayCount(dayCount)
                .currentDayIndex(currentDayIndex)
                .build();
    }

    private static ParticipantTotals buildParticipants(ParticipantStatisticsResponse stats) {
        long total = nz(stats.getTotalParticipants());
        long collected = nz(stats.getBibCollectedCount());
        return ParticipantTotals.builder()
                .total(total)
                .collected(collected)
                .pending(nz(stats.getPendingCount()))
                .collectedPercent(percent(collected, total))
                .build();
    }

    private static GenderBreakdown buildGender(ParticipantStatisticsResponse stats) {
        ParticipantStatisticsResponse.GenderStatistics g = stats.getGenderBreakdown();
        if (g == null) {
            return GenderBreakdown.builder().build();
        }
        return GenderBreakdown.builder()
                .male(nz(g.getMale()))
                .female(nz(g.getFemale()))
                .other(nz(g.getOther()))
                .build();
    }

    private static List<RaceStat> buildRaces(ParticipantStatisticsResponse stats) {
        if (stats.getRaceBreakdown() == null) {
            return List.of();
        }
        return stats.getRaceBreakdown().stream()
                .map(r -> {
                    long total = nz(r.getCount());
                    long collected = nz(r.getBibCollectedCount());
                    return RaceStat.builder()
                            .raceId(r.getRaceId())
                            .raceName(r.getRaceName())
                            .total(total)
                            .collected(collected)
                            .collectedPercent(percent(collected, total))
                            .build();
                })
                .toList();
    }

    private static List<CategoryStat> buildCategories(ParticipantStatisticsResponse stats, EventNames names) {
        if (stats.getCategoryBreakdown() == null) {
            return List.of();
        }
        return stats.getCategoryBreakdown().stream()
                .map(c -> CategoryStat.builder()
                        .raceId(names.categoryRaceId(c.getCategoryId()))
                        .categoryId(c.getCategoryId())
                        .categoryName(c.getCategoryName())
                        .total(nz(c.getCount()))
                        .build())
                .toList();
    }

    private static long nz(Integer value) {
        return value != null ? value : 0L;
    }

    private static double percent(long part, long whole) {
        if (whole <= 0) {
            return 0.0;
        }
        return Math.round(part * 1000.0 / whole) / 10.0;
    }

    private static LocalDate localDate(Instant instant, ZoneId zone) {
        return instant != null ? instant.atZone(zone).toLocalDate() : null;
    }

    private static String format(Instant instant, ZoneId zone) {
        return instant != null ? instant.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null;
    }
}
