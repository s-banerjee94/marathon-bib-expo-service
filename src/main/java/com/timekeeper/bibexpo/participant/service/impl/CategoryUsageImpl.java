package com.timekeeper.bibexpo.participant.service.impl;

import com.timekeeper.bibexpo.event.api.CategoryUsage;
import com.timekeeper.bibexpo.participant.repository.ParticipantDDBRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryUsageImpl implements CategoryUsage {

    private final ParticipantDDBRepository participantRepository;

    @Override
    public long countParticipants(Long eventId, Long categoryId) {
        log.info("Counting participants for event ID: {} and category ID: {}", eventId, categoryId);

        long count = participantRepository.countByEventAndCategory(eventId, categoryId);

        log.info("Found {} participants for category ID: {} in event ID: {}", count, categoryId, eventId);
        return count;
    }
}
