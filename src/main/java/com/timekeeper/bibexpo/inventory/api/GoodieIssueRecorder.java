package com.timekeeper.bibexpo.inventory.api;

import java.util.List;

/**
 * Writes the ledger lines for goodies handed over at the counter, and for hand-overs taken back.
 *
 * <p>A hand-over never waits for stock: the runner is already holding the goody, so a shelf may go
 * below zero, and the ledger shows exactly how far.
 */
public interface GoodieIssueRecorder {

    /**
     * Takes one unit off the shelf per hand-over, each as an {@code ISSUE} line referencing the bib.
     *
     * @param issues    what was handed over, as {@link GoodieStockQuery#planIssue} planned it
     * @param bibNumber the participant who received them
     * @param actor     username of whoever handed them over
     */
    void issue(List<GoodieIssue> issues, String bibNumber, String actor);

    /**
     * Puts back one unit per hand-over taken back, each as a {@code REVERSAL} line, at the location
     * the unit originally left.
     *
     * @param issues    the hand-overs being undone, as they were recorded
     * @param bibNumber the participant they are taken back from
     * @param actor     username of whoever undid them
     */
    void reverse(List<GoodieIssue> issues, String bibNumber, String actor);
}
