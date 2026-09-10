package com.timekeeper.bibexpo.participant.api;

import java.util.Optional;

/**
 * Port that lets goody removal ask outer slices whether something they own still points at the
 * goody, without depending on their repositories directly — each slice provides its own
 * implementation, the same shape as {@code EventDeletionGuard}.
 */
public interface GoodieRemovalGuard {

    /**
     * The reason this slice will not let the goody be removed, written for the organizer to read.
     * Removal is blocked while any slice reports one.
     *
     * @param eventId    the event the goody belongs to
     * @param goodieName the goody about to be removed, as the event's list names it
     * @return the blocking reason, or empty when the slice holds nothing for this goody
     */
    Optional<String> findBlockingReason(Long eventId, String goodieName);
}
