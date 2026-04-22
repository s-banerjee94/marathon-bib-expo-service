package com.timekeeper.bibexpo.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.exception.UserNotFoundException;
import com.timekeeper.bibexpo.model.dto.response.AppStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.EventStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.OrganizationStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.UserStatisticsResponse;
import com.timekeeper.bibexpo.model.dto.response.stats.EventStatsData;
import com.timekeeper.bibexpo.model.dto.response.stats.OrganizationStatsData;
import com.timekeeper.bibexpo.model.dto.response.stats.UserStatsData;
import com.timekeeper.bibexpo.model.entity.AppStatisticsSnapshot;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.model.enums.StatisticsScope;
import com.timekeeper.bibexpo.repository.AppStatisticsSnapshotRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.service.AppStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppStatisticsServiceImpl implements AppStatisticsService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final AppStatisticsSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.statistics.stale-threshold-minutes:15}")
    private int staleThresholdMinutes;

    private static final UserRole[] ORG_ROLES = {
            UserRole.ORGANIZER_ADMIN, UserRole.ORGANIZER_USER, UserRole.DISTRIBUTOR
    };

    private static final Set<UserRole> ALLOWED_ROLES = Set.of(
            UserRole.ROOT, UserRole.ADMIN, UserRole.ORGANIZER_ADMIN, UserRole.ORGANIZER_USER
    );

    @Override
    @Transactional
    public UserStatisticsResponse getUserStatistics(User currentUser) {
        log.info("Fetching user statistics for: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        validateAccess(currentUser);
        AppStatisticsResponse snapshot = loadOrRefresh(currentUser);
        return toUserResponse(snapshot, currentUser);
    }

    @Override
    @Transactional
    public UserStatisticsResponse refreshUserStatistics(User currentUser) {
        log.info("Refreshing user statistics by: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        AppStatisticsResponse snapshot = forceRefresh(currentUser);
        return toUserResponse(snapshot, currentUser);
    }

    @Override
    @Transactional
    public OrganizationStatisticsResponse getOrganizationStatistics(User currentUser) {
        log.info("Fetching organization statistics for: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        // Always GLOBAL — ROOT/ADMIN only (enforced at controller level via @PreAuthorize)
        AppStatisticsResponse snapshot = loadOrRefresh(StatisticsScope.GLOBAL, AppStatisticsSnapshot.GLOBAL_SCOPE_SENTINEL);
        return OrganizationStatisticsResponse.builder()
                .scope(StatisticsScope.GLOBAL)
                .refreshedAt(snapshot.getRefreshedAt())
                .organizations(snapshot.getOrganizations())
                .build();
    }

    @Override
    @Transactional
    public OrganizationStatisticsResponse refreshOrganizationStatistics(User currentUser) {
        log.info("Refreshing organization statistics by: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        AppStatisticsResponse snapshot = computeAndPersist(StatisticsScope.GLOBAL, AppStatisticsSnapshot.GLOBAL_SCOPE_SENTINEL);
        return OrganizationStatisticsResponse.builder()
                .scope(StatisticsScope.GLOBAL)
                .refreshedAt(snapshot.getRefreshedAt())
                .organizations(snapshot.getOrganizations())
                .build();
    }

    @Override
    @Transactional
    public EventStatisticsResponse getEventStatistics(User currentUser) {
        log.info("Fetching event statistics for: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        validateAccess(currentUser);
        AppStatisticsResponse snapshot = loadOrRefresh(currentUser);
        return toEventResponse(snapshot, currentUser);
    }

    @Override
    @Transactional
    public EventStatisticsResponse refreshEventStatistics(User currentUser) {
        log.info("Refreshing event statistics by: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        AppStatisticsResponse snapshot = forceRefresh(currentUser);
        return toEventResponse(snapshot, currentUser);
    }

    private AppStatisticsResponse loadOrRefresh(User currentUser) {
        if (isGlobalScope(currentUser)) {
            return loadOrRefresh(StatisticsScope.GLOBAL, AppStatisticsSnapshot.GLOBAL_SCOPE_SENTINEL);
        }
        User user = fetchUserWithOrganization(currentUser.getUsername());
        validateOrgUserHasOrganization(user);
        return loadOrRefresh(StatisticsScope.ORGANIZATION, user.getOrganization().getId());
    }

    private AppStatisticsResponse forceRefresh(User currentUser) {
        if (isGlobalScope(currentUser)) {
            return computeAndPersist(StatisticsScope.GLOBAL, AppStatisticsSnapshot.GLOBAL_SCOPE_SENTINEL);
        }
        User user = fetchUserWithOrganization(currentUser.getUsername());
        validateOrgUserHasOrganization(user);
        return computeAndPersist(StatisticsScope.ORGANIZATION, user.getOrganization().getId());
    }

    private AppStatisticsResponse loadOrRefresh(StatisticsScope scope, Long scopeKey) {
        return snapshotRepository.findByScopeAndOrganizationId(scope, scopeKey)
                .filter(s -> !isStale(s))
                .map(s -> {
                    log.debug("Returning cached {} snapshot (refreshed at: {})", scope, s.getRefreshedAt());
                    return deserialize(s, scopeKey);
                })
                .orElseGet(() -> {
                    log.debug("{} snapshot missing or stale — triggering refresh", scope);
                    return computeAndPersist(scope, scopeKey);
                });
    }

    private AppStatisticsResponse computeAndPersist(StatisticsScope scope, Long scopeKey) {
        AppStatisticsResponse stats = scope == StatisticsScope.GLOBAL
                ? buildGlobalStats()
                : buildOrgStats(scopeKey);

        persist(scope, scopeKey, stats);
        log.info("Statistics snapshot persisted — scope: {} scopeKey: {}", scope, scopeKey);
        return stats;
    }

    private UserStatisticsResponse toUserResponse(AppStatisticsResponse snapshot, User currentUser) {
        UserStatisticsResponse.UserStatisticsResponseBuilder builder = UserStatisticsResponse.builder()
                .scope(snapshot.getScope())
                .refreshedAt(snapshot.getRefreshedAt())
                .users(snapshot.getUsers());

        if (!isGlobalScope(currentUser)) {
            User user = fetchUserWithOrganization(currentUser.getUsername());
            builder.organizationId(snapshot.getOrganizationId())
                    .organizationName(user.getOrganization().getOrganizerName());
        }

        return builder.build();
    }

    private EventStatisticsResponse toEventResponse(AppStatisticsResponse snapshot, User currentUser) {
        EventStatisticsResponse.EventStatisticsResponseBuilder builder = EventStatisticsResponse.builder()
                .scope(snapshot.getScope())
                .refreshedAt(snapshot.getRefreshedAt())
                .events(snapshot.getEvents());

        if (!isGlobalScope(currentUser)) {
            User user = fetchUserWithOrganization(currentUser.getUsername());
            builder.organizationId(snapshot.getOrganizationId())
                    .organizationName(user.getOrganization().getOrganizerName());
        }

        return builder.build();
    }

    private AppStatisticsResponse buildGlobalStats() {
        long total = userRepository.countByDeletedFalse();
        long active = userRepository.countByEnabledTrueAndDeletedFalse();
        long inactive = userRepository.countByEnabledFalseAndDeletedFalse();

        Map<UserRole, Long> byRole = new EnumMap<>(UserRole.class);
        Arrays.stream(UserRole.values())
                .forEach(role -> byRole.put(role, userRepository.countByRoleAndDeletedFalse(role)));

        return AppStatisticsResponse.builder()
                .scope(StatisticsScope.GLOBAL)
                .refreshedAt(LocalDateTime.now())
                .users(UserStatsData.builder()
                        .total(total)
                        .active(active)
                        .inactive(inactive)
                        .byRole(byRole)
                        .build())
                .organizations(buildGlobalOrganizationStats())
                .events(buildGlobalEventStats())
                .build();
    }

    private AppStatisticsResponse buildOrgStats(Long orgId) {
        long total = userRepository.countByOrganizationIdAndDeletedFalse(orgId);
        long active = userRepository.countByOrganizationIdAndEnabledTrueAndDeletedFalse(orgId);
        long inactive = userRepository.countByOrganizationIdAndEnabledFalseAndDeletedFalse(orgId);

        Map<UserRole, Long> byRole = new EnumMap<>(UserRole.class);
        Arrays.stream(ORG_ROLES)
                .forEach(role -> byRole.put(role, userRepository.countByOrganizationIdAndRoleAndDeletedFalse(orgId, role)));

        return AppStatisticsResponse.builder()
                .scope(StatisticsScope.ORGANIZATION)
                .organizationId(orgId)
                .refreshedAt(LocalDateTime.now())
                .users(UserStatsData.builder()
                        .total(total)
                        .active(active)
                        .inactive(inactive)
                        .byRole(byRole)
                        .build())
                .organizations(null)
                .events(buildOrgEventStats(orgId))
                .build();
    }

    private OrganizationStatsData buildGlobalOrganizationStats() {
        long total = organizationRepository.countByDeletedFalse();
        long active = organizationRepository.countByEnabledTrueAndDeletedFalse();
        long inactive = organizationRepository.countByEnabledFalseAndDeletedFalse();

        Map<String, Long> byTier = toStringMap(organizationRepository.countGroupBySubscriptionTier());
        Map<String, Long> byStatus = toStringMap(organizationRepository.countGroupBySubscriptionStatus());

        return OrganizationStatsData.builder()
                .total(total)
                .active(active)
                .inactive(inactive)
                .bySubscriptionTier(byTier)
                .bySubscriptionStatus(byStatus)
                .build();
    }

    private EventStatsData buildGlobalEventStats() {
        long total = eventRepository.count();
        long upcoming = eventRepository.countUpcoming(LocalDateTime.now());

        Map<EventStatus, Long> byStatus = new EnumMap<>(EventStatus.class);
        Arrays.stream(EventStatus.values())
                .forEach(s -> byStatus.put(s, eventRepository.countByStatus(s)));

        return EventStatsData.builder()
                .total(total)
                .upcoming(upcoming)
                .byStatus(byStatus)
                .build();
    }

    private EventStatsData buildOrgEventStats(Long orgId) {
        long total = eventRepository.countByOrganizationId(orgId);
        long upcoming = eventRepository.countUpcomingByOrganizationId(orgId, LocalDateTime.now());

        Map<EventStatus, Long> byStatus = new EnumMap<>(EventStatus.class);
        Arrays.stream(EventStatus.values())
                .forEach(s -> byStatus.put(s, eventRepository.countByOrganizationIdAndStatus(orgId, s)));

        return EventStatsData.builder()
                .total(total)
                .upcoming(upcoming)
                .byStatus(byStatus)
                .build();
    }

    private void persist(StatisticsScope scope, Long scopeKey, AppStatisticsResponse stats) {
        snapshotRepository.upsert(scope.name(), scopeKey, serialize(stats), LocalDateTime.now());
    }

    private String serialize(AppStatisticsResponse stats) {
        try {
            return objectMapper.writeValueAsString(stats);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize statistics snapshot", e);
            throw new IllegalStateException("Failed to serialize statistics snapshot", e);
        }
    }

    private AppStatisticsResponse deserialize(AppStatisticsSnapshot snapshot, Long scopeKey) {
        try {
            AppStatisticsResponse stats = objectMapper.readValue(snapshot.getSnapshotData(), AppStatisticsResponse.class);
            stats.setRefreshedAt(snapshot.getRefreshedAt());
            return stats;
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize snapshot (id: {}), triggering refresh", snapshot.getId(), e);
            return computeAndPersist(snapshot.getScope(), scopeKey);
        }
    }

    private boolean isStale(AppStatisticsSnapshot snapshot) {
        return snapshot.getRefreshedAt().isBefore(LocalDateTime.now().minusMinutes(staleThresholdMinutes));
    }

    private boolean isGlobalScope(User user) {
        return user.getRole() == UserRole.ROOT || user.getRole() == UserRole.ADMIN;
    }

    private void validateAccess(User user) {
        if (!ALLOWED_ROLES.contains(user.getRole())) {
            log.warn("User {} with role {} attempted to access statistics", user.getUsername(), user.getRole());
            throw new UnauthorizedAccessException("You are not allowed to access statistics.");
        }
    }

    private User fetchUserWithOrganization(String username) {
        return userRepository.findByUsernameWithOrganization(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    }

    private void validateOrgUserHasOrganization(User user) {
        if (user.getOrganization() == null) {
            log.error("Organization user {} has no organization assigned", user.getUsername());
            throw new UnauthorizedAccessException("Your account is not assigned to an organization.");
        }
    }

    /** Converts JPQL GROUP BY Object[] rows into a Map&lt;String, Long&gt;. Null keys become "UNKNOWN". */
    private Map<String, Long> toStringMap(List<Object[]> rows) {
        Map<String, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            String key = row[0] != null ? (String) row[0] : "UNKNOWN";
            result.put(key, (Long) row[1]);
        }
        return result;
    }
}
