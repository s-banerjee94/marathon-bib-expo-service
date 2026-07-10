package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.EventDeletionNotAllowedException;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.EventLimitRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.RaceRepository;
import com.timekeeper.bibexpo.service.EventBillingGuard;
import com.timekeeper.bibexpo.service.EventDeletionGuard;
import com.timekeeper.bibexpo.service.NotificationService;
import com.timekeeper.bibexpo.service.StorageService;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.service.validator.EventStatusTransitionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    private static final Long EVENT_ID = 1L;

    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private EventLimitRepository eventLimitRepository;
    @Mock
    private RaceRepository raceRepository;
    @Mock
    private EventAccessValidator eventAccessValidator;
    @Mock
    private EventStatusTransitionValidator statusTransitionValidator;
    @Mock
    private EventBillingGuard eventBillingGuard;
    @Mock
    private EventDeletionGuard deletionGuard;
    @Mock
    private StorageService storageService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ObjectMapper objectMapper;

    private EventServiceImpl eventService;

    private Event event;
    private User currentUser;

    @BeforeEach
    void setUp() {
        eventService = new EventServiceImpl(
                eventRepository, organizationRepository, eventLimitRepository, raceRepository,
                eventAccessValidator, statusTransitionValidator, eventBillingGuard,
                List.of(deletionGuard), storageService, notificationService,
                eventPublisher, objectMapper);

        event = Event.builder()
                .id(EVENT_ID)
                .eventName("Test Marathon")
                .status(EventStatus.DRAFT)
                .build();
        currentUser = User.builder().username("tester").build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    @Test
    void deleteEventIsBlockedWhileAGuardReportsContent() {
        when(raceRepository.countByEventIdAndDeletedFalse(EVENT_ID)).thenReturn(0);
        when(deletionGuard.findBlockingContent(EVENT_ID)).thenReturn(Optional.of("SMS templates"));

        assertThatThrownBy(() -> eventService.deleteEvent(EVENT_ID, currentUser))
                .isInstanceOf(EventDeletionNotAllowedException.class)
                .hasMessage("You cannot delete this event while it still has SMS templates. Delete them first.");

        verify(eventRepository, never()).delete(any(Event.class));
    }

    @Test
    void deleteEventIsBlockedByRacesBeforeGuardsAreConsulted() {
        when(raceRepository.countByEventIdAndDeletedFalse(EVENT_ID)).thenReturn(2);

        assertThatThrownBy(() -> eventService.deleteEvent(EVENT_ID, currentUser))
                .isInstanceOf(EventDeletionNotAllowedException.class)
                .hasMessage("You cannot delete this event while it still has races. Delete them first.");

        verify(deletionGuard, never()).findBlockingContent(any());
        verify(eventRepository, never()).delete(any(Event.class));
    }

    @Test
    void deleteEventProceedsWhenEmptyAndNoGuardObjects() {
        when(raceRepository.countByEventIdAndDeletedFalse(EVENT_ID)).thenReturn(0);
        when(deletionGuard.findBlockingContent(EVENT_ID)).thenReturn(Optional.empty());

        eventService.deleteEvent(EVENT_ID, currentUser);

        verify(eventLimitRepository).deleteById(EVENT_ID);
        verify(eventRepository).delete(event);
    }
}
