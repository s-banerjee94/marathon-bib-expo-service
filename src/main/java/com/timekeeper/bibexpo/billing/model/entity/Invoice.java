package com.timekeeper.bibexpo.billing.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One generated bill for an event — a header carrying the rollup totals plus a snapshot of
 * the event/organization at generation time (a later rename never alters a past bill). The
 * charge breakdown — the participant fee and any extra charges/discounts — lives in
 * {@link InvoiceLineItem} rows keyed by this bill's id. Pricing inputs (unit price, tax rate,
 * currency) are platform config ({@code BillingRates}), not stored per bill.
 *
 * <p>Rows are written by the Python billing Lambda; the Spring app reads them, edits drafts,
 * and flips {@code paymentStatus}.
 */
@Entity
@Table(name = "invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_invoice_number", columnNames = {"invoice_number"})
        },
        indexes = {
                @Index(name = "idx_invoice_event", columnList = "event_id"),
                @Index(name = "idx_invoice_organization", columnList = "organization_id"),
                @Index(name = "idx_invoice_created_at", columnList = "created_at"),
                @Index(name = "idx_invoice_reason", columnList = "reason"),
                @Index(name = "idx_invoice_payment_status", columnList = "payment_status"),
                @Index(name = "idx_invoice_status", columnList = "status")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice implements Serializable {

    /** Stable unique id of this bill (full uuid) — the primary key. */
    @Id
    @Column(name = "bill_id", length = 36)
    private String billId;

    /** GST tax-invoice serial (e.g. INV/2026-27/0001); null while the bill is a draft. */
    @Column(name = "invoice_number", length = 32)
    private String invoiceNumber;

    // DB default backfills legacy rows (real bills) to FINAL; new Java-built bills start DRAFT.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(16) not null default 'FINAL'")
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    // ---- snapshot of the event/organization at generation time ----
    @Column(name = "event_name", nullable = false)
    private String eventName;

    /** Organizer name captured at generation time; backs the global feed's text search and top-orgs rollup. */
    @Column(name = "organizer_name", nullable = false)
    private String organizerName;

    /** Event start date captured when the bill was generated. */
    @Column(name = "event_date", nullable = false)
    private Instant eventDate;

    // ---- rollup totals (computed from the line items) ----
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    /** S3 object key of the rendered tax-invoice PDF. Set by the Lambda on a FINAL; null on a draft (previewed on the frontend). */
    @Column(name = "pdf_key")
    private String pdfKey;

    /** What triggered it — AUTO (timer) or MANUAL (on-demand request). */
    @Column(nullable = false, length = 16)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    /** When this bill was generated. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** When this bill was last modified (line-item edits while a draft); null until first edited. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** When this bill became a FINAL (issued) receivable; set by the billing Lambda on finalize, null on a draft. Drives bill-stats range windows, aging and DSO. */
    @Column(name = "finalized_at")
    private Instant finalizedAt;

    /** When this bill was marked PAID; set by Spring on the one-way payment transition (PAID is final, never reverted). Drives the collected/outstanding split. */
    @Column(name = "paid_at")
    private Instant paidAt;
}
