package com.timekeeper.bibexpo.event.service.impl;

import com.timekeeper.bibexpo.event.api.EventStore;
import com.timekeeper.bibexpo.event.exception.EventNotFoundException;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.model.entity.EventGoodie;
import com.timekeeper.bibexpo.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventStoreImpl implements EventStore {

    private final EventRepository eventRepository;

    @Override
    public Event requireById(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    @Override
    public Optional<Event> findById(Long eventId) {
        return eventRepository.findById(eventId);
    }

    @Override
    public long countByOrganizationId(Long organizationId) {
        return eventRepository.countByOrganizationId(organizationId);
    }

    @Override
    @Transactional
    public void markDistributionStarted(Long eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            if (!Boolean.TRUE.equals(event.getDistributionStarted())) {
                event.setDistributionStarted(true);
                eventRepository.save(event);
            }
        });
    }

    @Override
    @Transactional
    public void saveGoodies(Long eventId, List<EventGoodie> goodies) {
        eventRepository.findById(eventId).ifPresent(event -> {
            event.setEventGoodies(new ArrayList<>(goodies));
            eventRepository.save(event);
        });
    }
}
