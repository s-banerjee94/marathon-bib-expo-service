package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.EventLatestImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Tracks the most recent batch import per event.
 * One row per event, overwritten (upserted) on every new batch import.
 */
@Repository
public interface EventLatestImportRepository extends JpaRepository<EventLatestImport, Long> {
}
