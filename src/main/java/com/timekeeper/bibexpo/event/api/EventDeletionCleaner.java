package com.timekeeper.bibexpo.event.api;

/**
 * Port that lets core event deletion hand each outer slice a chance to remove what it holds for an
 * event, without depending on their repositories directly. Runs only after every
 * {@link EventDeletionGuard} has passed, and inside the deletion transaction, so a slice that
 * fails to clean up rolls the whole delete back instead of leaving rows behind an event that no
 * longer exists.
 *
 * <p>Content the user is expected to remove themselves belongs in a guard; this is for records
 * they never see, such as import history.
 */
public interface EventDeletionCleaner {

    /**
     * Removes everything this slice holds for the event.
     *
     * @param eventId the event being deleted
     */
    void purgeForEvent(Long eventId);
}
