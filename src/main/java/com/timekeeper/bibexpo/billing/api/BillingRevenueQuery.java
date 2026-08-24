package com.timekeeper.bibexpo.billing.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What the platform has actually collected, for the modules that report on it.
 *
 * <p>Revenue is read straight off the invoices ledger — FINAL bills marked paid, summed by the
 * instant they were paid. The ledger itself, its statuses and the rates that produced the amounts
 * stay inside billing; this interface is the whole of what a reporting surface gets.
 */
public interface BillingRevenueQuery {

    /**
     * Sums money collected in a half-open window.
     *
     * @param from inclusive lower bound, or {@code null} for no lower bound
     * @param to   exclusive upper bound, or {@code null} to run open-ended to now
     * @return the amount collected, never null — zero when nothing was paid in the window
     */
    BigDecimal collectedBetween(Instant from, Instant to);

    /**
     * @return the currency every billed amount is denominated in
     */
    String currency();
}
