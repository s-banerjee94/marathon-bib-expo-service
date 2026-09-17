package com.timekeeper.bibexpo.inventory.api;

import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;

import java.util.List;
import java.util.Map;

/**
 * What the distribution counter needs to know about an event's goodies before handing one over:
 * which are linked to inventory, and which variant a hand-over takes off the shelf.
 *
 * <p>Distribution sits above inventory, so it asks here rather than reading inventory's tables. An
 * event that links nothing gets nothing back and hands out exactly as it always did.
 */
public interface GoodieStockQuery {

    /**
     * Every goody the event has linked to an inventory item, with the variants each can go out as.
     *
     * @param eventId the event
     * @return one entry per linked goody, in goody-name order; empty when the event links nothing
     */
    List<GoodieStockOption> optionsFor(Long eventId);

    /**
     * Decides what handing some goodies over takes off the shelf, without touching stock. A variant the
     * counter chose always wins, so a participant can swap sizes. Otherwise one of the participant's own
     * goodies goes out as the variant their value reads as, the way the check screen reads it, and an
     * item with a single variant needs no choice. Every goody is checked before any is refused, so a
     * refusal names all the goodies that fail it.
     *
     * @param eventId  the event
     * @param requests the goodies handed over together, each under a different name
     * @return what to post once the hand-over is recorded, by goody name; a goody that is not linked, or
     *         whose value was taught to mean nothing is owed, has no entry
     * @throws InvalidUserDataException if a goody needs a variant and none was chosen, or a chosen variant
     *                                  is not one of its item's
     */
    Map<String, GoodieIssue> planIssues(Long eventId, List<GoodieRequest> requests);
}
