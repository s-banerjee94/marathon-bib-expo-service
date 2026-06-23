package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Live platform-wide counts. Used both for the trend's last (current) bucket and by the
 * daily snapshot job. The {@code *AsOf} variants derive cumulative counts from entity
 * createdAt timestamps to seed historical trend buckets.
 */
@Service
@RequiredArgsConstructor
public class PlatformLiveStatsQueries {

    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public int countOrganizations() {
        return (int) organizationRepository.countByDeletedFalse();
    }

    @Transactional(readOnly = true)
    public int countTotalEvents() {
        return (int) eventRepository.count();
    }

    @Transactional(readOnly = true)
    public int countActiveEvents() {
        return (int) eventRepository.countByStatus(EventStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public int countTotalUsers() {
        return (int) userRepository.count();
    }

    @Transactional(readOnly = true)
    public int countDistinctCities() {
        return (int) eventRepository.countDistinctCities();
    }

    // --- Historical (createdAt-derived) cumulative counts for backfill ---

    @Transactional(readOnly = true)
    public int countOrganizationsAsOf(Instant asOf) {
        return (int) organizationRepository.countByDeletedFalseAndCreatedAtLessThanEqual(asOf);
    }

    @Transactional(readOnly = true)
    public int countTotalEventsAsOf(Instant asOf) {
        return (int) eventRepository.countByCreatedAtLessThanEqual(asOf);
    }

    /** Approximation: currently-PUBLISHED events that existed by {@code asOf} (historical status is not recoverable). */
    @Transactional(readOnly = true)
    public int countActiveEventsAsOf(Instant asOf) {
        return (int) eventRepository.countByStatusAndCreatedAtLessThanEqual(EventStatus.PUBLISHED, asOf);
    }

    @Transactional(readOnly = true)
    public int countTotalUsersAsOf(Instant asOf) {
        return (int) userRepository.countByCreatedAtLessThanEqual(asOf);
    }

    @Transactional(readOnly = true)
    public int countDistinctCitiesAsOf(Instant asOf) {
        return (int) eventRepository.countDistinctCitiesAsOf(asOf);
    }
}
