package com.timekeeper.bibexpo.service;

import java.util.Optional;

/**
 * Port that lets core event deletion consult outer slices for content still tied to an
 * event, without depending on their repositories directly — each slice provides its own
 * implementation. Keeps the dependency pointing from the slice to core, same as
 * {@link EventBillingGuard}.
 */
public interface EventDeletionGuard {

    /**
     * Label of content this slice still holds for the event (e.g. "SMS templates"), used
     * in the user-facing rejection message. Deletion is blocked while any slice reports
     * content.
     *
     * @param eventId the event about to be deleted
     * @return the first blocking content label, or empty when the slice holds nothing
     */
    Optional<String> findBlockingContent(Long eventId);
}
