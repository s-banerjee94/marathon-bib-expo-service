package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LiveStatsQueries {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public int countTotalEvents(Long orgId) {
        return (int) eventRepository.countByOrganizationId(orgId);
    }

    @Transactional(readOnly = true)
    public int countActiveEvents(Long orgId) {
        return (int) eventRepository.countByOrganizationIdAndStatus(orgId, EventStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public int countTotalUsers(Long orgId) {
        return (int) userRepository.countByOrganizationId(orgId);
    }

    @Transactional(readOnly = true)
    public int countDistinctCities(Long orgId) {
        return (int) eventRepository.countDistinctCitiesByOrg(orgId);
    }
}
