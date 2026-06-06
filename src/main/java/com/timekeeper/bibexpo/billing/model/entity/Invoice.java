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
 * One generated bill for an event. An event accumulates many of these — one per
 * completion that produced a new participant count (dedup-by-count happens in the
 * billing Lambda). Every row is an immutable snapshot of what was charged: the
 * event name and date are copied in at generation time so a later rename or
 * reschedule never alters a past bill.
 *
 * <p>Rows are written exclusively by the Python billing Lambda; the Spring app only
 * reads them (and flips {@code paymentStatus} via the admin endpoint).
 */
@Entity
@Table(name = "invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_invoice_bill_id", columnNames = {"bill_id"})
        },
        indexes = {
                @Index(name = "idx_invoice_event", columnList = "event_id"),
                @Index(name = "idx_invoice_organization", columnList = "organization_id"),
                @Index(name = "idx_invoice_created_at", columnList = "created_at"),
                @Index(name = "idx_invoice_reason", columnList = "reason"),
                @Index(name = "idx_invoice_payment_status", columnList = "payment_status")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable unique id of this bill (full uuid); the human-facing invoice identifier. */
    @Column(name = "bill_id", nullable = false, unique = true, length = 36)
    private String billId;

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

    // ---- charge breakdown ----
    @Column(name = "participant_count", nullable = false)
    private Long participantCount;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    /** S3 object key of the rendered PDF; presigned to a download URL at read time. */
    @Column(name = "pdf_key", nullable = false)
    private String pdfKey;

    /** What triggered it — AUTO (5h post-completion timer) or MANUAL (on-demand request). */
    @Column(nullable = false, length = 16)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    /** When this bill was generated. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
