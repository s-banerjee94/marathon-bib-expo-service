package com.timekeeper.bibexpo.importer.service.impl;

import com.timekeeper.bibexpo.event.api.EventImportUsage;
import com.timekeeper.bibexpo.importer.model.enums.ImportMode;
import com.timekeeper.bibexpo.importer.repository.ImportJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventImportUsageImpl implements EventImportUsage {

    private final ImportJobRepository importJobRepository;

    @Override
    public long countFullImports(Long eventId) {
        return importJobRepository.countByEventIdAndMode(eventId, ImportMode.IMPORT);
    }

    @Override
    public long countAddOnImports(Long eventId) {
        return importJobRepository.countByEventIdAndMode(eventId, ImportMode.ADD_ON);
    }
}
