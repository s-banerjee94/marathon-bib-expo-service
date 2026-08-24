package com.timekeeper.bibexpo.reporting.repository;

import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.model.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Reporting's own read side over the events table. The rollups need counts, groupings and
 * top-N slices that no other module asks for, so they live here rather than widening
 * {@code EventRepository}, which stays the event module's private write side.
 *
 * <p>Read-only by construction: nothing here mutates, and reporting never saves an event.
 */
public interface ReportingEventRepository extends JpaRepository<Event, Long> {

    long countByStatus(EventStatus status);

    long countByOrganizationId(Long organizationId);

    long countByOrganizationIdAndStatus(Long organizationId, EventStatus status);

    // --- Organization dashboard ---

    @Query("SELECT COUNT(e) FROM Event e WHERE e.organization.id = :orgId AND (:from IS NULL OR e.eventStartDate >= :from) AND (:to IS NULL OR e.eventStartDate <= :to)")
    long countByOrganizationIdAndRange(@Param("orgId") Long orgId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.organization.id = :orgId AND e.status = com.timekeeper.bibexpo.event.model.entity.EventStatus.PUBLISHED AND e.eventStartDate > :now AND (:from IS NULL OR e.eventStartDate >= :from) AND (:to IS NULL OR e.eventStartDate <= :to)")
    long countUpcomingByOrganizationIdAndRange(@Param("orgId") Long orgId, @Param("now") Instant now, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT e.status, COUNT(e) FROM Event e WHERE e.organization.id = :orgId AND (:from IS NULL OR e.eventStartDate >= :from) AND (:to IS NULL OR e.eventStartDate <= :to) GROUP BY e.status")
    List<Object[]> countGroupByStatusForOrgAndRange(@Param("orgId") Long orgId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT COUNT(DISTINCT LOWER(TRIM(city))) FROM events WHERE organization_id = :orgId AND status != 'CANCELLED' AND city IS NOT NULL AND city != ''", nativeQuery = true)
    long countDistinctCitiesByOrg(@Param("orgId") Long orgId);

    @Query(value = "SELECT COUNT(DISTINCT LOWER(TRIM(city))) FROM events WHERE organization_id = :orgId AND status != 'CANCELLED' AND city IS NOT NULL AND city != '' AND (:from IS NULL OR event_start_date >= :from) AND (:to IS NULL OR event_start_date <= :to)", nativeQuery = true)
    long countDistinctCitiesByOrgAndRange(@Param("orgId") Long orgId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT MAX(city), LOWER(TRIM(city)) AS city_key, COUNT(*) AS cnt FROM events WHERE organization_id = :orgId AND status != 'CANCELLED' AND city IS NOT NULL AND city != '' AND (:from IS NULL OR event_start_date >= :from) AND (:to IS NULL OR event_start_date <= :to) GROUP BY city_key ORDER BY cnt DESC, city_key ASC LIMIT :topN", nativeQuery = true)
    List<Object[]> findTopCitiesForOrgAndRange(@Param("orgId") Long orgId, @Param("from") Instant from, @Param("to") Instant to, @Param("topN") int topN);

    List<Event> findTop4ByOrganizationIdAndStatusOrderByEventStartDateAsc(Long orgId, EventStatus status);

    List<Event> findTop10ByOrganizationIdOrderByCreatedAtDesc(Long orgId);

    // --- Platform (global) dashboard ---

    @Query("SELECT COUNT(e) FROM Event e WHERE (:from IS NULL OR e.eventStartDate >= :from) AND (:to IS NULL OR e.eventStartDate <= :to)")
    long countByRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.status IN (com.timekeeper.bibexpo.event.model.entity.EventStatus.DRAFT, com.timekeeper.bibexpo.event.model.entity.EventStatus.PUBLISHED) AND (:from IS NULL OR e.eventStartDate >= :from) AND (:to IS NULL OR e.eventStartDate <= :to)")
    long countActiveByRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT e.status, COUNT(e) FROM Event e WHERE (:from IS NULL OR e.eventStartDate >= :from) AND (:to IS NULL OR e.eventStartDate <= :to) GROUP BY e.status")
    List<Object[]> countGroupByStatusForRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT COUNT(DISTINCT LOWER(TRIM(city))) FROM events WHERE status != 'CANCELLED' AND city IS NOT NULL AND city != '' AND (:from IS NULL OR event_start_date >= :from) AND (:to IS NULL OR event_start_date <= :to)", nativeQuery = true)
    long countDistinctCitiesForRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT MAX(city), LOWER(TRIM(city)) AS city_key, COUNT(*) AS cnt FROM events WHERE status != 'CANCELLED' AND city IS NOT NULL AND city != '' AND (:from IS NULL OR event_start_date >= :from) AND (:to IS NULL OR event_start_date <= :to) GROUP BY city_key ORDER BY cnt DESC, city_key ASC LIMIT :topN", nativeQuery = true)
    List<Object[]> findTopCitiesForRange(@Param("from") Instant from, @Param("to") Instant to, @Param("topN") int topN);

    List<Event> findTop4ByStatusAndEventStartDateGreaterThanOrderByEventStartDateAsc(EventStatus status, Instant now);

    /** Top organizations by event count, as (organizationId, eventCount) rows, descending. */
    @Query(value = "SELECT e.organization_id, COUNT(*) AS cnt FROM events e GROUP BY e.organization_id ORDER BY cnt DESC LIMIT :topN", nativeQuery = true)
    List<Object[]> findTopOrganizationIdsByEventCount(@Param("topN") int topN);

    // --- Trend backfill / live cumulative counts ---

    long countByCreatedAtLessThanEqual(Instant asOf);

    long countByStatusAndCreatedAtLessThanEqual(EventStatus status, Instant asOf);

    @Query(value = "SELECT COUNT(DISTINCT LOWER(TRIM(city))) FROM events WHERE status != 'CANCELLED' AND city IS NOT NULL AND city != '' AND created_at <= :asOf", nativeQuery = true)
    long countDistinctCitiesAsOf(@Param("asOf") Instant asOf);

    @Query(value = "SELECT COUNT(DISTINCT LOWER(TRIM(city))) FROM events WHERE status != 'CANCELLED' AND city IS NOT NULL AND city != ''", nativeQuery = true)
    long countDistinctCities();
}
