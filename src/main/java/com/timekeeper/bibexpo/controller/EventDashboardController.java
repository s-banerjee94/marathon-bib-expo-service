package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.response.dashboard.EventDashboardResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.EventActivityRange;
import com.timekeeper.bibexpo.service.dashboard.EventDashboardService;
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
}
