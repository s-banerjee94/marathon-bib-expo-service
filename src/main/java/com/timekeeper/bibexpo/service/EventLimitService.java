package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.request.UpdateEventLimitRequest;
import com.timekeeper.bibexpo.model.dto.response.EventLimitResponse;
import com.timekeeper.bibexpo.model.entity.User;

/**
 * Manages per-event resource limit reads and admin overrides.
 */
public interface EventLimitService {

    EventLimitResponse getEventLimits(Long eventId, User currentUser);

    EventLimitResponse updateEventLimits(Long eventId, UpdateEventLimitRequest request, User currentUser);
}
