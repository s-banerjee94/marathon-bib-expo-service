package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

    Optional<Organization> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Organization> findByTaxId(String taxId);

    boolean existsByTaxId(String taxId);

    boolean existsByOrganizerName(String organizerName);

    boolean existsByPhoneNumber(String phoneNumber);

    @Query("SELECT o.id FROM Organization o WHERE o.enabled = true")
    List<Long> findAllActiveIds();

    // --- Statistics count queries ---
    long countByEnabledTrue();

    long countByEnabledFalse();

    @Query("SELECT o.subscriptionTier, COUNT(o) FROM Organization o GROUP BY o.subscriptionTier")
    List<Object[]> countGroupBySubscriptionTier();

    @Query("SELECT o.subscriptionStatus, COUNT(o) FROM Organization o GROUP BY o.subscriptionStatus")
    List<Object[]> countGroupBySubscriptionStatus();

    // --- Platform (global) dashboard queries ---

    @Query("SELECT COUNT(o) FROM Organization o WHERE (:from IS NULL OR o.createdAt >= :from) AND (:to IS NULL OR o.createdAt <= :to)")
    long countByCreatedAtRange(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("SELECT o.subscriptionTier, COUNT(o) FROM Organization o WHERE (:from IS NULL OR o.createdAt >= :from) AND (:to IS NULL OR o.createdAt <= :to) GROUP BY o.subscriptionTier")
    List<Object[]> countGroupBySubscriptionTierAndRange(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("SELECT o.subscriptionStatus, COUNT(o) FROM Organization o WHERE (:from IS NULL OR o.createdAt >= :from) AND (:to IS NULL OR o.createdAt <= :to) GROUP BY o.subscriptionStatus")
    List<Object[]> countGroupBySubscriptionStatusAndRange(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    List<Organization> findTop5ByOrderByCreatedAtDesc();

    List<Organization> findByIdIn(List<Long> ids);

    // --- Trend backfill / live cumulative counts ---

    long countByCreatedAtLessThanEqual(java.time.Instant asOf);

    @Query("SELECT MIN(o.createdAt) FROM Organization o")
    java.time.Instant findMinCreatedAt();
}
