package com.timekeeper.bibexpo.participant.service.impl;

import com.timekeeper.bibexpo.event.api.CategoryUsage;
import com.timekeeper.bibexpo.participant.repository.ParticipantDDBRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryUsageImpl implements CategoryUsage {

    private final ParticipantDDBRepository participantRepository;

    @Override
    public long countParticipants(Long eventId, Long categoryId) {
        return participantRepository.countByEventAndCategory(eventId, categoryId);
    }
}
