package com.timekeeper.bibexpo.billing.model.entity;

/**
 * The type of an {@link InvoiceLineItem}. {@code PARTICIPANT} is the system-generated
 * participant fee (one per bill, written by the billing pipeline); the rest are extra
 * charges or adjustments. {@code DISCOUNT} lines carry a negative amount.
 */
public enum LineItemKind {
    PARTICIPANT,
    SMS_CAMPAIGN,
    EXTRA_USER,
    DISCOUNT,
    SURCHARGE,
    CUSTOM
}
