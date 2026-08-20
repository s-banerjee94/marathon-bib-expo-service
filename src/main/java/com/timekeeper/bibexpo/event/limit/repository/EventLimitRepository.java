package com.timekeeper.bibexpo.event.limit.repository;

import com.timekeeper.bibexpo.event.limit.model.entity.EventLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventLimitRepository extends JpaRepository<EventLimit, Long> {

    Optional<EventLimit> findByEventId(Long eventId);
}
