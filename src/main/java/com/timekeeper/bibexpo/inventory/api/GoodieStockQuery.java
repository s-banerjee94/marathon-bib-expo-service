package com.timekeeper.bibexpo.inventory.api;

import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;

import java.util.List;
import java.util.Optional;

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
     * Decides what handing one goody over takes off the shelf, without touching stock. An item that
     * varies by nothing needs no choice; one that varies needs the counter to say which.
     *
     * @param eventId    the event
     * @param goodieName the goody being handed over
     * @param variantId  the variant the counter chose, or null when it chose none
     * @return what to post once the hand-over is recorded; empty when the goody is not linked
     * @throws InvalidUserDataException if the item varies and no variant was chosen, or the chosen
     *                                  variant is not one of the item's
     */
    Optional<GoodieIssue> planIssue(Long eventId, String goodieName, Long variantId);
}
