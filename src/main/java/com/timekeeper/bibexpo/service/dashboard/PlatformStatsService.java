package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.model.dto.response.dashboard.CityCountDto;
import com.timekeeper.bibexpo.model.dto.response.dashboard.OrgListItemDto;
import com.timekeeper.bibexpo.model.dto.response.dashboard.PlatformEventsDto;
import com.timekeeper.bibexpo.model.dto.response.dashboard.PlatformOrganizationsDto;
import com.timekeeper.bibexpo.model.dto.response.dashboard.PlatformUsersDto;
import com.timekeeper.bibexpo.model.dto.response.dashboard.TopOrgDto;
import com.timekeeper.bibexpo.model.dto.response.dashboard.UpcomingEventDto;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.model.enums.DashboardRange;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the organizations, events, and users blocks of the platform dashboard from live counts.
 */
@Service
@RequiredArgsConstructor
public class PlatformStatsService {

    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PlatformOrganizationsDto buildOrganizationsBlock(PlatformDashboardQuery query) {
        Instant from  = rangeFrom(query.getRange());
        Instant tfrom = rangeFrom(query.getTierRange());
        Instant sfrom = rangeFrom(query.getStatusRange());

        long total = organizationRepository.countByDeletedFalseAndCreatedAtRange(from, null);
        Map<String, Long> byTier   = toStringMap(organizationRepository.countGroupBySubscriptionTierAndRange(tfrom, null));
        Map<String, Long> byStatus = toStringMap(organizationRepository.countGroupBySubscriptionStatusAndRange(sfrom, null));

        List<OrgListItemDto> recent = organizationRepository.findTop5ByDeletedFalseOrderByCreatedAtDesc()
                .stream().map(OrgListItemDto::fromEntity).toList();

        return PlatformOrganizationsDto.builder()
                .total(total)
                .byTier(byTier)
                .byStatus(byStatus)
                .recent(recent)
                .top(buildTopOrganizations(query.getTopOrgs()))
                .build();
    }

    @Transactional(readOnly = true)
    public PlatformEventsDto buildEventsBlock(PlatformDashboardQuery query) {
        Instant from  = rangeFrom(query.getRange());
        Instant sfrom = rangeFrom(query.getStatusRange());
        Instant cfrom = rangeFrom(query.getCitiesRange());

        long total  = eventRepository.countByRange(from, null);
        long active = eventRepository.countActiveByRange(from, null);

        Map<String, Long> byStatus = buildEventByStatus(sfrom);
        List<CityCountDto> byCity = eventRepository.findTopCitiesForRange(cfrom, null, query.getTopCities())
                .stream()
                .map(row -> CityCountDto.builder().city((String) row[0]).count(((Number) row[2]).longValue()).build())
                .toList();
        int distinctCities = (int) eventRepository.countDistinctCitiesForRange(cfrom, null);

        List<UpcomingEventDto> upcoming = eventRepository
                .findTop4ByStatusAndEventStartDateGreaterThanOrderByEventStartDateAsc(EventStatus.PUBLISHED, Instant.now())
                .stream().map(UpcomingEventDto::fromEntity).toList();

        return PlatformEventsDto.builder()
                .total(total)
                .active(active)
                .byStatus(byStatus)
                .byCity(byCity)
                .distinctCities(distinctCities)
                .upcomingList(upcoming)
                .build();
    }

    @Transactional(readOnly = true)
    public PlatformUsersDto buildUsersBlock() {
        Map<String, Long> byRole = Arrays.stream(UserRole.values())
                .collect(Collectors.toMap(Enum::name, r -> 0L, (a, b) -> a, LinkedHashMap::new));
        userRepository.countGroupByRole()
                .forEach(row -> byRole.put(((UserRole) row[0]).name(), (Long) row[1]));

        return PlatformUsersDto.builder()
                .total(userRepository.count())
                .byRole(byRole)
                .build();
    }

    private List<TopOrgDto> buildTopOrganizations(int topN) {
        List<Object[]> rows = eventRepository.findTopOrganizationIdsByEventCount(topN);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> eventCountByOrg = new LinkedHashMap<>();
        rows.forEach(row -> eventCountByOrg.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));

        Map<Long, Organization> orgs = organizationRepository
                .findByIdInAndDeletedFalse(List.copyOf(eventCountByOrg.keySet()))
                .stream().collect(Collectors.toMap(Organization::getId, o -> o));

        return eventCountByOrg.entrySet().stream()
                .map(e -> orgs.get(e.getKey()))
                .filter(o -> o != null)
                .map(o -> TopOrgDto.builder()
                        .id(o.getId())
                        .organizerName(o.getOrganizerName())
                        .subscriptionTier(o.getSubscriptionTier())
                        .subscriptionStatus(o.getSubscriptionStatus())
                        .eventCount(eventCountByOrg.get(o.getId()))
                        .userCount(userRepository.countByOrganizationId(o.getId()))
                        .createdAt(o.getCreatedAt())
                        .build())
                .toList();
    }

    private Map<String, Long> buildEventByStatus(Instant from) {
        Map<String, Long> result = Arrays.stream(EventStatus.values())
                .collect(Collectors.toMap(Enum::name, s -> 0L, (a, b) -> a, LinkedHashMap::new));
        eventRepository.countGroupByStatusForRange(from, null)
                .forEach(row -> result.put(((EventStatus) row[0]).name(), (Long) row[1]));
        return result;
    }

    /** Maps JPQL GROUP BY rows to a String→Long map; null keys (unset tier/status) become "UNKNOWN". */
    private Map<String, Long> toStringMap(List<Object[]> rows) {
        Map<String, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            String key = row[0] != null ? (String) row[0] : "UNKNOWN";
            result.put(key, (Long) row[1]);
        }
        return result;
    }

    private Instant rangeFrom(DashboardRange range) {
        return switch (range) {
            case YEAR  -> Instant.now().minus(365, ChronoUnit.DAYS);
            case MONTH -> Instant.now().minus(30, ChronoUnit.DAYS);
            case ALL   -> null;
        };
    }
}
