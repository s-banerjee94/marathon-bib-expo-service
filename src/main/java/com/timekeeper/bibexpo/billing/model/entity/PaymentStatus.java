package com.timekeeper.bibexpo.billing.model.entity;

/** Whether an invoice has been settled. Set manually by ROOT/ADMIN; defaults to UNPAID at generation. */
public enum PaymentStatus {
    PAID,
    UNPAID
}
