package com.timekeeper.bibexpo.reporting.service;

import com.timekeeper.bibexpo.event.model.entity.EventStatus;
import com.timekeeper.bibexpo.reporting.repository.ReportingEventRepository;
import com.timekeeper.bibexpo.reporting.repository.ReportingUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LiveStatsQueries {

    private final ReportingEventRepository reportingEventRepository;
    private final ReportingUserRepository reportingUserRepository;

    @Transactional(readOnly = true)
    public int countTotalEvents(Long orgId) {
        return (int) reportingEventRepository.countByOrganizationId(orgId);
    }

    @Transactional(readOnly = true)
    public int countActiveEvents(Long orgId) {
        return (int) reportingEventRepository.countByOrganizationIdAndStatus(orgId, EventStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public int countTotalUsers(Long orgId) {
        return (int) reportingUserRepository.countByOrganizationId(orgId);
    }

    @Transactional(readOnly = true)
    public int countDistinctCities(Long orgId) {
        return (int) reportingEventRepository.countDistinctCitiesByOrg(orgId);
    }
}
