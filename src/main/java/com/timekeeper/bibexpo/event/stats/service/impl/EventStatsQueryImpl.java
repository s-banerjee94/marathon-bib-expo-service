package com.timekeeper.bibexpo.event.stats.service.impl;

import com.timekeeper.bibexpo.event.api.EventStatsQuery;
import com.timekeeper.bibexpo.event.api.GoodieEntitlement;
import com.timekeeper.bibexpo.event.stats.model.dynamodb.EventStatsDDB;
import com.timekeeper.bibexpo.event.stats.repository.EventStatsDDBRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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

    @Override
    public List<GoodieEntitlement> entitlements(Long eventId) {
        return counters(eventId).stream()
                .map(GoodieEntitlement::from)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(GoodieEntitlement::goodieName)
                        .thenComparing(GoodieEntitlement::value))
                .toList();
    }

}
