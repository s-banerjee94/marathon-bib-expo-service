package com.timekeeper.bibexpo.reporting.service;

import com.timekeeper.bibexpo.reporting.model.dto.response.CityCountDto;
import com.timekeeper.bibexpo.reporting.model.dto.response.EventListItemDto;
import com.timekeeper.bibexpo.reporting.model.dto.response.EventsDashboardDto;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.model.entity.EventStatus;
import com.timekeeper.bibexpo.shared.model.enums.DashboardRange;
import com.timekeeper.bibexpo.reporting.repository.ReportingEventRepository;
import com.timekeeper.bibexpo.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardEventStatsService {

    private final ReportingEventRepository reportingEventRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public EventsDashboardDto buildEventsBlock(OrgDashboardQuery query) {
        Long orgId = query.getOrgId();
        Instant from  = rangeFrom(query.getRange());
        Instant sfrom = rangeFrom(query.getStatusRange());
        Instant cfrom = rangeFrom(query.getCitiesRange());
        Instant now   = Instant.now();

        long total    = reportingEventRepository.countByOrganizationIdAndRange(orgId, from, null);
        long upcoming = reportingEventRepository.countUpcomingByOrganizationIdAndRange(orgId, now, from, null);

        Map<String, Long> byStatus = buildByStatus(orgId, sfrom, null);
        List<CityCountDto> byCity  = buildByCity(orgId, cfrom, null, query.getTopCities());
        int distinctCities = (int) reportingEventRepository.countDistinctCitiesByOrgAndRange(orgId, cfrom, null);

        List<EventListItemDto> active = reportingEventRepository
                .findTop4ByOrganizationIdAndStatusOrderByEventStartDateAsc(orgId, EventStatus.PUBLISHED)
                .stream().map(this::toListItem).toList();

        List<EventListItemDto> recent = reportingEventRepository
                .findTop10ByOrganizationIdOrderByCreatedAtDesc(orgId)
                .stream().map(this::toListItem).toList();

        return EventsDashboardDto.builder()
                .total(total)
                .upcoming(upcoming)
                .byStatus(byStatus)
                .byCity(byCity)
                .distinctCities(distinctCities)
                .active(active)
                .recent(recent)
                .build();
    }

    /** Maps an event to a dashboard list item, presigning a short-lived URL for its logo. */
    private EventListItemDto toListItem(Event event) {
        EventListItemDto dto = EventListItemDto.fromEntity(event);
        dto.setLogoUrl(storageService.createDownloadUrl(event.getLogoObjectKey()));
        return dto;
    }

    private Map<String, Long> buildByStatus(Long orgId, Instant from, Instant to) {
        Map<String, Long> result = Arrays.stream(EventStatus.values())
                .collect(Collectors.toMap(Enum::name, s -> 0L, (a, b) -> a, LinkedHashMap::new));

        reportingEventRepository.countGroupByStatusForOrgAndRange(orgId, from, to)
                .forEach(row -> result.put(((EventStatus) row[0]).name(), (Long) row[1]));

        return result;
    }

    private List<CityCountDto> buildByCity(Long orgId, Instant from, Instant to, int topN) {
        return reportingEventRepository.findTopCitiesForOrgAndRange(orgId, from, to, topN)
                .stream()
                .map(row -> CityCountDto.builder()
                        .city((String) row[0])
                        .count((Long) row[2])
                        .build())
                .toList();
    }

    private Instant rangeFrom(DashboardRange range) {
        return switch (range) {
            case YEAR  -> Instant.now().minus(365, ChronoUnit.DAYS);
            case MONTH -> Instant.now().minus(30, ChronoUnit.DAYS);
            case ALL   -> null;
        };
    }
}
