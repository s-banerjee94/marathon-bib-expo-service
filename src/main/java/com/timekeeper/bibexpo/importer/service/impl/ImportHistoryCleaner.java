package com.timekeeper.bibexpo.importer.service.impl;

import com.timekeeper.bibexpo.event.api.EventDeletionCleaner;
import com.timekeeper.bibexpo.importer.repository.ImportJobRepository;
import com.timekeeper.bibexpo.importer.repository.ImportRowErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Drops an event's import history when the event is deleted. Import jobs carry the event id as a
 * plain column rather than an association, so nothing in the database removes them on its own.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImportHistoryCleaner implements EventDeletionCleaner {

    private final ImportJobRepository importJobRepository;
    private final ImportRowErrorRepository importRowErrorRepository;

    @Override
    public void purgeForEvent(Long eventId) {
        // Errors first: they are found through their owning job, so the subquery needs it present.
        int errors = importRowErrorRepository.deleteByEventId(eventId);
        int jobs = importJobRepository.deleteByEventId(eventId);
        if (jobs > 0) {
            log.info("Deleted {} import job(s) and {} row error(s) for event {}", jobs, errors, eventId);
        }
    }
}
