package com.timekeeper.bibexpo.event.repository;

import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.model.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    List<Event> findByStatus(EventStatus status);

    boolean existsByEventNameAndOrganizationId(String eventName, Long organizationId);

    long countByOrganizationId(Long organizationId);
}
