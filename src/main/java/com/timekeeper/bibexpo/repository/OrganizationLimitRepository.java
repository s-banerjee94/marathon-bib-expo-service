package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.OrganizationLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationLimitRepository extends JpaRepository<OrganizationLimit, Long> {

    // --- Atomic usage-counter operations ---
    // Each increment is guarded by the matching cap in a single UPDATE so
    // concurrent user creates cannot exceed the limit. Returns the number of rows
    // updated: 1 when the slot was reserved, 0 when the cap is reached.

    @Modifying(flushAutomatically = true)
    @Query("UPDATE OrganizationLimit l SET l.usedAdmins = l.usedAdmins + 1 " +
            "WHERE l.organizationId = :orgId AND l.usedAdmins < l.maxAdmins")
    int tryIncrementAdmins(@Param("orgId") Long orgId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE OrganizationLimit l SET l.usedOrganizerUsers = l.usedOrganizerUsers + 1 " +
            "WHERE l.organizationId = :orgId AND l.usedOrganizerUsers < l.maxOrganizerUsers")
    int tryIncrementOrganizerUsers(@Param("orgId") Long orgId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE OrganizationLimit l SET l.usedDistributors = l.usedDistributors + 1 " +
            "WHERE l.organizationId = :orgId AND l.usedDistributors < l.maxDistributors")
    int tryIncrementDistributors(@Param("orgId") Long orgId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE OrganizationLimit l SET l.usedAdmins = l.usedAdmins - 1 WHERE l.organizationId = :orgId AND l.usedAdmins > 0")
    int decrementAdmins(@Param("orgId") Long orgId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE OrganizationLimit l SET l.usedOrganizerUsers = l.usedOrganizerUsers - 1 WHERE l.organizationId = :orgId AND l.usedOrganizerUsers > 0")
    int decrementOrganizerUsers(@Param("orgId") Long orgId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE OrganizationLimit l SET l.usedDistributors = l.usedDistributors - 1 WHERE l.organizationId = :orgId AND l.usedDistributors > 0")
    int decrementDistributors(@Param("orgId") Long orgId);
}
