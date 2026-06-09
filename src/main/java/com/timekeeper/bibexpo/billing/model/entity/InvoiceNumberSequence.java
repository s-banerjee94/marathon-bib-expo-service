package com.timekeeper.bibexpo.billing.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-financial-year counter backing the gap-free GST invoice serial
 * ({@code INV/{financialYear}/{number}}). One row per Indian financial year
 * (April–March); the counter resets when a new year's first final bill is issued.
 *
 * <p>Schema only — the Spring app owns the table via Hibernate but never reads or
 * writes it. The billing Lambda allocates numbers from it atomically (MySQL
 * {@code LAST_INSERT_ID()} upsert) when finalizing a bill.
 */
@Entity
@Table(name = "invoice_number_sequence")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceNumberSequence {

    /** Indian financial year, e.g. {@code 2026-27}. */
    @Id
    @Column(name = "financial_year", length = 7)
    private String financialYear;

    /** Highest serial issued so far in this financial year. */
    @Column(name = "last_number", nullable = false)
    @Builder.Default
    private long lastNumber = 0L;
}
