package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.ImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, String> {

    Page<ImportJob> findByEventIdOrderByImportedAtDesc(Long eventId, Pageable pageable);

    Optional<ImportJob> findByImportIdAndEventId(String importId, Long eventId);
}
