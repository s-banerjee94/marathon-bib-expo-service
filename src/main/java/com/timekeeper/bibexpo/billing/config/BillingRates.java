package com.timekeeper.bibexpo.billing.config;

import java.math.BigDecimal;

/**
 * Platform billing rates held in code rather than as per-invoice columns — the inputs used
 * to calculate a bill (participant unit price, GST rate, currency). The billing Lambda owns
 * its own copy via the {@code UNIT_PRICE} / {@code GST_RATE_PERCENT} / {@code CURRENCY}
 * environment variables; this record MUST be kept in sync with those.
 */
public record BillingRates(BigDecimal unitPrice, BigDecimal taxRate, String currency) {

    public static final BillingRates DEFAULT =
            new BillingRates(new BigDecimal("5"), new BigDecimal("18"), "INR");
}
