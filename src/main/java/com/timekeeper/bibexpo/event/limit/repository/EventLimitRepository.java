package com.timekeeper.bibexpo.event.limit.repository;

import com.timekeeper.bibexpo.event.limit.model.entity.EventLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventLimitRepository extends JpaRepository<EventLimit, Long> {

    Optional<EventLimit> findByEventId(Long eventId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE EventLimit l SET l.usedImports = l.usedImports + 1 WHERE l.eventId = :eventId")
    int incrementUsedImports(@Param("eventId") Long eventId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE EventLimit l SET l.usedAddOns = l.usedAddOns + 1 WHERE l.eventId = :eventId")
    int incrementUsedAddOns(@Param("eventId") Long eventId);
}
