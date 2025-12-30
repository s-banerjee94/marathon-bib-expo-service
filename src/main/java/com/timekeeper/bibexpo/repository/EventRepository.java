package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    Optional<Event> findByIdAndDeletedFalse(Long id);

    List<Event> findByDeletedFalse();

    List<Event> findByOrganizationIdAndDeletedFalse(Long organizationId);

    List<Event> findByStatusAndDeletedFalse(EventStatus status);

    boolean existsByEventNameAndOrganizationIdAndDeletedFalse(String eventName, Long organizationId);
}
