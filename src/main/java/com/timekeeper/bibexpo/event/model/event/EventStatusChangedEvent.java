package com.timekeeper.bibexpo.event.model.event;

import com.timekeeper.bibexpo.event.model.entity.EventStatus;

/** Published after an event's status change commits, so auto-billing can arm or cancel its timer. */
public record EventStatusChangedEvent(Long eventId, EventStatus newStatus) {
}
