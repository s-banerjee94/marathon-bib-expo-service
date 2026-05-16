package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    List<Event> findByOrganizationId(Long organizationId);

    List<Event> findByStatus(EventStatus status);

    boolean existsByEventNameAndOrganizationId(String eventName, Long organizationId);

    // --- Statistics count queries ---
    long countByStatus(EventStatus status);

    long countByOrganizationId(Long organizationId);

    long countByOrganizationIdAndStatus(Long organizationId, EventStatus status);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.status = 'PUBLISHED' AND e.eventStartDate > :now")
    long countUpcoming(@Param("now") Instant now);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.organization.id = :orgId AND e.status = 'PUBLISHED' AND e.eventStartDate > :now")
    long countUpcomingByOrganizationId(@Param("orgId") Long orgId, @Param("now") Instant now);
}
