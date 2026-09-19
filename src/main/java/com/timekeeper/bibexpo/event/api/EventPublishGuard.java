package com.timekeeper.bibexpo.event.api;

import java.util.Optional;

/**
 * Port that lets core event publishing ask outer slices whether the event is still missing
 * something they own, without depending on their repositories directly — each slice provides its
 * own implementation. Keeps the dependency pointing from the slice to core, same as
 * {@link EventDeletionGuard}.
 *
 * <p>Unlike that guard the slice returns the finished sentence rather than a label to compose
 * into one, because only the slice knows both what is missing and what the organizer has to do
 * about it.
 */
public interface EventPublishGuard {

    /**
     * The reason this slice will not let the event be published, written for the organizer to
     * read. Publishing is blocked while any slice reports one.
     *
     * @param eventId the event about to be published
     * @return the blocking reason, or empty when the slice has nothing to object to
     */
    Optional<String> findBlockingReason(Long eventId);
}
