package com.timekeeper.bibexpo.billing.service.util;

import com.timekeeper.bibexpo.billing.model.entity.InvoiceLineItem;
import com.timekeeper.bibexpo.billing.model.entity.LineItemKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Rolls a bill's line items up into its money totals, mirroring the billing Lambda's authoritative
 * {@code _recompute_totals} so a draft stays self-consistent between edits (the Lambda still owns
 * the final compute and PDF at finalize). A percentage-discount line's {@code amount} is re-derived
 * against the current pre-tax charge base and written back in place, so callers must persist the
 * passed-in lines after calling this.
 */
public final class BillTotalsCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    private BillTotalsCalculator() {
    }

    /** The recomputed header totals (each at 2 dp, HALF_UP). */
    public record Totals(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount) {
    }

    /**
     * Recompute the totals from {@code lineItems}, re-deriving each percentage discount against the
     * pre-tax charge base and mutating its {@code amount} in place.
     *
     * @param lineItems    every line on the bill (participant fee, charges, discounts)
     * @param taxRatePercent GST rate as a percent (e.g. 18)
     */
    public static Totals recompute(List<InvoiceLineItem> lineItems, BigDecimal taxRatePercent) {
        BigDecimal base = BigDecimal.ZERO;
        for (InvoiceLineItem line : lineItems) {
            if (line.getKind() != LineItemKind.DISCOUNT) {
                base = base.add(amountOf(line));
            }
        }
        base = base.setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal discountSum = BigDecimal.ZERO;
        for (InvoiceLineItem line : lineItems) {
            if (line.getKind() != LineItemKind.DISCOUNT) {
                continue;
            }
            if (line.getDiscountPercent() != null) {
                BigDecimal derived = line.getDiscountPercent()
                        .multiply(base)
                        .divide(HUNDRED, SCALE, RoundingMode.HALF_UP)
                        .negate();
                line.setAmount(derived);
                discountSum = discountSum.add(derived);
            } else {
                discountSum = discountSum.add(amountOf(line));
            }
        }

        BigDecimal subtotal = base.add(discountSum).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.multiply(taxRatePercent)
                .divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax).setScale(SCALE, RoundingMode.HALF_UP);
        return new Totals(subtotal, tax, total);
    }

    private static BigDecimal amountOf(InvoiceLineItem line) {
        return line.getAmount() != null ? line.getAmount() : BigDecimal.ZERO;
    }
}
