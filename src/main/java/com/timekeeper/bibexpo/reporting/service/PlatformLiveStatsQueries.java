package com.timekeeper.bibexpo.reporting.service;

import com.timekeeper.bibexpo.event.model.entity.EventStatus;
import com.timekeeper.bibexpo.reporting.repository.ReportingOrganizationRepository;
import com.timekeeper.bibexpo.reporting.repository.ReportingEventRepository;
import com.timekeeper.bibexpo.reporting.repository.ReportingUserRepository;
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

    private final ReportingOrganizationRepository reportingOrganizationRepository;
    private final ReportingEventRepository reportingEventRepository;
    private final ReportingUserRepository reportingUserRepository;

    @Transactional(readOnly = true)
    public int countOrganizations() {
        return (int) reportingOrganizationRepository.count();
    }

    @Transactional(readOnly = true)
    public int countTotalEvents() {
        return (int) reportingEventRepository.count();
    }

    @Transactional(readOnly = true)
    public int countActiveEvents() {
        return (int) reportingEventRepository.countByStatus(EventStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public int countTotalUsers() {
        return (int) reportingUserRepository.count();
    }

    @Transactional(readOnly = true)
    public int countDistinctCities() {
        return (int) reportingEventRepository.countDistinctCities();
    }

    // --- Historical (createdAt-derived) cumulative counts for backfill ---

    @Transactional(readOnly = true)
    public int countOrganizationsAsOf(Instant asOf) {
        return (int) reportingOrganizationRepository.countByCreatedAtLessThanEqual(asOf);
    }

    @Transactional(readOnly = true)
    public int countTotalEventsAsOf(Instant asOf) {
        return (int) reportingEventRepository.countByCreatedAtLessThanEqual(asOf);
    }

    /** Approximation: currently-PUBLISHED events that existed by {@code asOf} (historical status is not recoverable). */
    @Transactional(readOnly = true)
    public int countActiveEventsAsOf(Instant asOf) {
        return (int) reportingEventRepository.countByStatusAndCreatedAtLessThanEqual(EventStatus.PUBLISHED, asOf);
    }

    @Transactional(readOnly = true)
    public int countTotalUsersAsOf(Instant asOf) {
        return (int) reportingUserRepository.countByCreatedAtLessThanEqual(asOf);
    }

    @Transactional(readOnly = true)
    public int countDistinctCitiesAsOf(Instant asOf) {
        return (int) reportingEventRepository.countDistinctCitiesAsOf(asOf);
    }
}
