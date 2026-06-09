package com.timekeeper.bibexpo.billing.model.entity;

/**
 * Lifecycle state of a bill.
 */
public enum InvoiceStatus {

    /** Proforma: no invoice number, not payable, no effect on stats; replaceable. */
    DRAFT,

    /** Issued GST tax invoice: numbered, immutable, payable. */
    FINAL
}
