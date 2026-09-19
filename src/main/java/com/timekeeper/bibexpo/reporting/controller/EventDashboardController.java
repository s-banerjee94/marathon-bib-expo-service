package com.timekeeper.bibexpo.reporting.controller;

import com.timekeeper.bibexpo.reporting.model.dto.response.EventDashboardResponse;
import com.timekeeper.bibexpo.reporting.model.enums.EventActivityRange;
import com.timekeeper.bibexpo.reporting.service.EventDashboardService;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * REST controller for the event-details Dashboard tab rollup endpoint.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class EventDashboardController implements EventDashboardControllerApi {

    private final EventDashboardService eventDashboardService;

    @Override
    public ResponseEntity<EventDashboardResponse> getEventDashboard(
            Long eventId, EventActivityRange range, User currentUser) {
        EventDashboardResponse response = eventDashboardService.loadDashboard(eventId, range, currentUser);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePrivate())
                .body(response);
    }

    @Override
    public ResponseEntity<EventDashboardResponse> reconcileEventDashboard(
            Long eventId, EventActivityRange range, User currentUser) {
        log.info("POST /events/{}/dashboard/reconcile by user {}", eventId, currentUser.getUsername());
        return ResponseEntity.ok(eventDashboardService.reconcileDashboard(eventId, range, currentUser));
    }
}
