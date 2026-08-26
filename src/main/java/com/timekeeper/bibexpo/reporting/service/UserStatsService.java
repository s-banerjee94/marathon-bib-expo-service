package com.timekeeper.bibexpo.reporting.service;

import com.timekeeper.bibexpo.reporting.model.dto.response.UserCountsDto;
import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.reporting.repository.ReportingUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserStatsService {

    private static final List<UserRole> ORG_ROLES =
            List.of(UserRole.ORGANIZER_ADMIN, UserRole.ORGANIZER_USER, UserRole.DISTRIBUTOR);

    private final ReportingUserRepository reportingUserRepository;

    @Transactional(readOnly = true)
    public UserCountsDto buildUsersBlock(Long orgId) {
        long total    = reportingUserRepository.countByOrganizationId(orgId);
        long active   = reportingUserRepository.countByOrganizationIdAndEnabledTrue(orgId);
        long inactive = reportingUserRepository.countByOrganizationIdAndEnabledFalse(orgId);

        Map<String, Long> byRole = new LinkedHashMap<>();
        ORG_ROLES.forEach(role -> byRole.put(role.name(), 0L));
        reportingUserRepository.countGroupByRoleForOrg(orgId).forEach(row ->
                byRole.computeIfPresent(((UserRole) row[0]).name(), (k, v) -> (Long) row[1]));

        return UserCountsDto.builder()
                .total(total)
                .active(active)
                .inactive(inactive)
                .byRole(byRole)
                .build();
    }
}
