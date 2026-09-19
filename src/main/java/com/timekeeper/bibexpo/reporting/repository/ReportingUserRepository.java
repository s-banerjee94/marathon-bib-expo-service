package com.timekeeper.bibexpo.reporting.repository;

import com.timekeeper.bibexpo.user.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Reporting's own read side over the users table. Only headcounts and role groupings, none of
 * which the user module itself has any use for.
 *
 * <p>Read-only by construction: nothing here mutates, and reporting never saves a user.
 */
public interface ReportingUserRepository extends JpaRepository<User, Long> {

    long countByOrganizationId(Long organizationId);

    long countByOrganizationIdAndEnabledTrue(Long organizationId);

    long countByOrganizationIdAndEnabledFalse(Long organizationId);

    @Query("SELECT u.role, COUNT(u) FROM User u WHERE u.organization.id = :orgId GROUP BY u.role")
    List<Object[]> countGroupByRoleForOrg(@Param("orgId") Long orgId);

    // --- Platform (global) dashboard ---

    @Query("SELECT u.role, COUNT(u) FROM User u GROUP BY u.role")
    List<Object[]> countGroupByRole();

    long countByCreatedAtLessThanEqual(Instant asOf);
}
