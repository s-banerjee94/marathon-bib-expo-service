package com.timekeeper.bibexpo.event.stats.service.impl;

import com.timekeeper.bibexpo.event.api.EventStatsQuery;
import com.timekeeper.bibexpo.event.api.GoodieEntitlement;
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
        return statsRepo.getCount(eventId.toString(), EventStatsDDB.KEY_TOTAL);
    }

    @Override
    public long pendingBibCount(Long eventId) {
        String id = eventId.toString();
        return Math.max(0, statsRepo.getCount(id, EventStatsDDB.KEY_TOTAL)
                - statsRepo.getCount(id, EventStatsDDB.KEY_BIB_COLLECTED));
    }

    @Override
    public long pendingGoodiesCount(Long eventId) {
        return Math.max(0, statsRepo.getCount(eventId.toString(), EventStatsDDB.KEY_GOODIES_PENDING));
    }

    @Override
    public List<EventStatsDDB> counters(Long eventId) {
        return statsRepo.queryAll(eventId.toString());
    }

    @Override
    public List<GoodieEntitlement> entitlements(Long eventId) {
        return GoodieEntitlement.fromRows(counters(eventId));
    }

}
