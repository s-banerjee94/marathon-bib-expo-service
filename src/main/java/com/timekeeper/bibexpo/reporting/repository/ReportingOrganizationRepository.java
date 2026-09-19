package com.timekeeper.bibexpo.reporting.repository;

import com.timekeeper.bibexpo.organization.model.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Reporting's own read side over the organizations table: the tier and status groupings, the
 * cumulative counts the trend backfill walks, and the top-N slices the platform rollup shows.
 *
 * <p>Read-only by construction: nothing here mutates, and reporting never saves an organization.
 */
public interface ReportingOrganizationRepository extends JpaRepository<Organization, Long> {

    @Query("SELECT o.id FROM Organization o WHERE o.enabled = true")
    List<Long> findAllActiveIds();

    List<Organization> findByIdIn(List<Long> ids);

    List<Organization> findTop5ByOrderByCreatedAtDesc();

    // --- Platform (global) dashboard ---

    @Query("SELECT COUNT(o) FROM Organization o WHERE (:from IS NULL OR o.createdAt >= :from) AND (:to IS NULL OR o.createdAt <= :to)")
    long countByCreatedAtRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT o.subscriptionTier, COUNT(o) FROM Organization o WHERE (:from IS NULL OR o.createdAt >= :from) AND (:to IS NULL OR o.createdAt <= :to) GROUP BY o.subscriptionTier")
    List<Object[]> countGroupBySubscriptionTierAndRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT o.subscriptionStatus, COUNT(o) FROM Organization o WHERE (:from IS NULL OR o.createdAt >= :from) AND (:to IS NULL OR o.createdAt <= :to) GROUP BY o.subscriptionStatus")
    List<Object[]> countGroupBySubscriptionStatusAndRange(@Param("from") Instant from, @Param("to") Instant to);

    // --- Trend backfill / live cumulative counts ---

    long countByCreatedAtLessThanEqual(Instant asOf);

    @Query("SELECT MIN(o.createdAt) FROM Organization o")
    Instant findMinCreatedAt();
}
