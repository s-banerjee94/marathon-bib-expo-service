package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.model.dto.response.dashboard.UserCountsDto;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.UserRepository;
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

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserCountsDto buildUsersBlock(Long orgId) {
        long total    = userRepository.countByOrganizationId(orgId);
        long active   = userRepository.countByOrganizationIdAndEnabledTrue(orgId);
        long inactive = userRepository.countByOrganizationIdAndEnabledFalse(orgId);

        Map<String, Long> byRole = new LinkedHashMap<>();
        ORG_ROLES.forEach(role -> byRole.put(role.name(), 0L));
        userRepository.countGroupByRoleForOrg(orgId).forEach(row ->
                byRole.computeIfPresent(((UserRole) row[0]).name(), (k, v) -> (Long) row[1]));

        return UserCountsDto.builder()
                .total(total)
                .active(active)
                .inactive(inactive)
                .byRole(byRole)
                .build();
    }
}
