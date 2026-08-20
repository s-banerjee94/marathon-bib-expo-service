package com.timekeeper.bibexpo.event.stats.service.impl;

import com.timekeeper.bibexpo.event.api.EventStatsQuery;
import com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB;
import com.timekeeper.bibexpo.event.stats.repository.EventStatsDDBRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventStatsQueryImpl implements EventStatsQuery {

    private final EventStatsDDBRepository statsRepo;

    @Override
    public long participantCount(Long eventId) {
        return statsRepo.getTotalParticipantCount(eventId.toString());
    }

    @Override
    public List<EventStatsDDB> counters(Long eventId) {
        return statsRepo.queryAll(eventId.toString());
    }
}
