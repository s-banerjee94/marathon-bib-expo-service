package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.ImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, String> {

    Page<ImportJob> findByEventIdOrderByImportedAtDesc(Long eventId, Pageable pageable);

    Optional<ImportJob> findByImportIdAndEventId(String importId, Long eventId);

    boolean existsByEventIdAndStatus(Long eventId, ImportJob.ImportStatus status);

    Optional<ImportJob> findByEventIdAndStatus(Long eventId, ImportJob.ImportStatus status);

    Optional<ImportJob> findByJobExecutionIdAndEventId(Long jobExecutionId, Long eventId);

    List<ImportJob> findByStatus(ImportJob.ImportStatus status);
}
