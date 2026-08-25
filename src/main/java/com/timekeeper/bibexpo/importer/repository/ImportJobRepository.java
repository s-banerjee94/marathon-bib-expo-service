package com.timekeeper.bibexpo.importer.repository;

import com.timekeeper.bibexpo.importer.model.entity.ImportJob;
import com.timekeeper.bibexpo.importer.model.enums.ImportMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, String> {

    Page<ImportJob> findByEventIdOrderByImportedAtDesc(Long eventId, Pageable pageable);

    Optional<ImportJob> findFirstByEventIdOrderByImportedAtDesc(Long eventId);

    Optional<ImportJob> findByImportIdAndEventId(String importId, Long eventId);

    boolean existsByEventIdAndStatus(Long eventId, ImportJob.ImportStatus status);

    Optional<ImportJob> findByEventIdAndStatus(Long eventId, ImportJob.ImportStatus status);

    Optional<ImportJob> findByJobExecutionIdAndEventId(Long jobExecutionId, Long eventId);

    List<ImportJob> findByStatus(ImportJob.ImportStatus status);

    int countByEventIdAndMode(Long eventId, ImportMode mode);

    /**
     * Clears an event's import history. The event id is a plain column with no association
     * behind it, so nothing removes these rows when the event goes.
     */
    @Modifying
    @Transactional
    @Query("delete from ImportJob j where j.eventId = :eventId")
    int deleteByEventId(@Param("eventId") Long eventId);
}
