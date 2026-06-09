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
 * One line on an {@link Invoice}: the system-generated PARTICIPANT fee, or a manual charge
 * or adjustment (an extra SMS campaign, extra distributor seats, a discount, a custom one-off).
 * Every charge — including the participant fee — is a line item; the invoice header carries
 * only the rolled-up totals.
 *
 * <p>{@code amount} is the line's signed contribution to the subtotal (negative for a discount);
 * {@code quantity} and {@code unitPrice} back a charge line (amount = quantity × unitPrice). For a
 * percentage discount, {@code discountPercent} holds the percent and {@code amount} is derived
 * against the charge base. Draft totals are recomputed in Java on each edit (BillTotalsCalculator);
 * the billing Lambda computes the authoritative totals at finalize. Lines are editable only while
 * the parent invoice is a DRAFT.
 */
@Entity
@Table(name = "invoice_line_item",
        indexes = {
                @Index(name = "idx_line_item_invoice", columnList = "invoice_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceLineItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Soft FK to the parent {@link Invoice}'s {@code billId} (no JPA association, matching the slice's convention). */
    @Column(name = "invoice_id", nullable = false, length = 36)
    private String invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LineItemKind kind;

    @Column(nullable = false)
    private String description;

    /** Informational quantity (e.g. number of SMS campaigns); null for flat adjustments. */
    @Column
    private Integer quantity;

    /** Informational per-unit price; null for flat adjustments. */
    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Signed contribution to the subtotal — negative for a discount. The billing Lambda's recompute
     * sums this; for a percentage discount it is re-derived from {@link #discountPercent}.
     */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /**
     * For a percentage DISCOUNT, the percent applied to the pre-tax charge base (participant
     * charge + non-discount lines); null for a fixed-amount line. Mutually exclusive with a
     * caller-supplied {@code amount} on a discount.
     */
    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    /** True if produced by the billing pipeline (refreshed on regen); false for user-entered lines (preserved). */
    @Column(name = "system_generated", nullable = false)
    @Builder.Default
    private boolean systemGenerated = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
