package com.timekeeper.bibexpo.config;

import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.OrganizationLimit;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.OrganizationLimitRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reconciles each organization's limits row against the actual user rows on
 * startup. Usage counters are maintained atomically on user create/delete, so
 * this backfills limits rows for pre-existing organizations and self-heals any
 * drift. Missing rows are created with default caps raised to at least the
 * current usage so no organization ends up capped below its real user count.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationUsageReconciler implements ApplicationRunner {

    private static final int DEFAULT_MAX_ADMINS = 1;
    private static final int DEFAULT_MAX_ORGANIZER_USERS = 1;
    private static final int DEFAULT_MAX_DISTRIBUTORS = 3;

    private final OrganizationRepository organizationRepository;
    private final OrganizationLimitRepository organizationLimitRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Organization> organizations = organizationRepository.findByDeletedFalse();
        int corrected = 0;

        for (Organization org : organizations) {
            int admins = (int) userRepository.countByOrganizationIdAndRole(org.getId(), UserRole.ORGANIZER_ADMIN);
            int users = (int) userRepository.countByOrganizationIdAndRole(org.getId(), UserRole.ORGANIZER_USER);
            int distributors = (int) userRepository.countByOrganizationIdAndRole(org.getId(), UserRole.DISTRIBUTOR);

            OrganizationLimit limit = organizationLimitRepository.findById(org.getId()).orElse(null);

            if (limit == null) {
                limit = OrganizationLimit.builder()
                        .organization(org)
                        .maxAdmins(Math.max(DEFAULT_MAX_ADMINS, admins))
                        .maxOrganizerUsers(Math.max(DEFAULT_MAX_ORGANIZER_USERS, users))
                        .maxDistributors(Math.max(DEFAULT_MAX_DISTRIBUTORS, distributors))
                        .usedAdmins(admins)
                        .usedOrganizerUsers(users)
                        .usedDistributors(distributors)
                        .build();
                organizationLimitRepository.save(limit);
                log.warn("Created missing limits row for organization {}: admins {}, users {}, distributors {}",
                        org.getId(), admins, users, distributors);
                corrected++;
            } else if (!matches(limit.getUsedAdmins(), admins)
                    || !matches(limit.getUsedOrganizerUsers(), users)
                    || !matches(limit.getUsedDistributors(), distributors)) {
                log.warn("Reconciling usage for organization {}: admins {}->{}, users {}->{}, distributors {}->{}",
                        org.getId(), limit.getUsedAdmins(), admins,
                        limit.getUsedOrganizerUsers(), users,
                        limit.getUsedDistributors(), distributors);
                limit.setUsedAdmins(admins);
                limit.setUsedOrganizerUsers(users);
                limit.setUsedDistributors(distributors);
                organizationLimitRepository.save(limit);
                corrected++;
            }
        }

        log.info("Organization usage reconciliation complete: {} organizations checked, {} corrected",
                organizations.size(), corrected);
    }

    private boolean matches(Integer stored, int actual) {
        return stored != null && stored == actual;
    }
}
