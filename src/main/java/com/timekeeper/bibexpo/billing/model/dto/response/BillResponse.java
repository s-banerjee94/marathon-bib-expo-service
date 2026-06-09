package com.timekeeper.bibexpo.billing.model.dto.response;

import com.timekeeper.bibexpo.billing.config.BillingRates;
import com.timekeeper.bibexpo.billing.model.entity.Invoice;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceLineItem;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceStatus;
import com.timekeeper.bibexpo.billing.model.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One bill for an event: header totals plus the line items that make them up. "
        + "Money fields (subtotal/taxAmount/totalAmount) are derived from the line items; taxRate and "
        + "currency are platform configuration, not per-bill values.")
public class BillResponse {

    @Schema(description = "Unique bill identifier", example = "9f1c2e3a-4b5d-6e7f-8a9b-0c1d2e3f4a5b")
    private String billId;

    @Schema(description = "Lifecycle status — DRAFT (replaceable, no number) or FINAL (issued, immutable)", example = "DRAFT")
    private InvoiceStatus status;

    @Schema(description = "GST tax-invoice serial; null while the bill is a draft", example = "INV/2026-27/0001")
    private String invoiceNumber;

    @Schema(description = "Event ID this bill belongs to", example = "42")
    private String eventId;

    @Schema(description = "Organization that was billed", example = "7")
    private Long organizationId;

    @Schema(description = "Event name captured when the bill was generated", example = "Mumbai Marathon 2026")
    private String eventName;

    @Schema(description = "Event start date captured at generation time (ISO-8601 instant)", example = "2026-01-19T01:30:00Z")
    private String eventDate;

    @Schema(description = "Charge before tax — the sum of every line item (charges minus discounts)", example = "6000.00")
    private BigDecimal subtotal;

    @Schema(description = "GST rate applied, percent (platform config, not stored per bill)", example = "18")
    private BigDecimal taxRate;

    @Schema(description = "GST amount — subtotal × taxRate", example = "1080.00")
    private BigDecimal taxAmount;

    @Schema(description = "Total payable — subtotal + taxAmount", example = "7080.00")
    private BigDecimal totalAmount;

    @Schema(description = "Currency code (platform config, not stored per bill)", example = "INR")
    private String currency;

    @Schema(description = "When the bill was generated (ISO-8601 instant)", example = "2026-01-19T07:00:00Z")
    private String createdAt;

    @Schema(description = "When the bill was last modified (ISO-8601 instant), or null if never edited", example = "2026-01-20T09:00:00Z")
    private String updatedAt;

    @Schema(description = "What triggered it — AUTO (the 24h post-completion timer) or MANUAL (on-demand request)", example = "AUTO")
    private String reason;

    @Schema(description = "Payment state — only meaningful on a FINAL bill; a DRAFT cannot be marked paid (defaults to UNPAID)", example = "UNPAID")
    private PaymentStatus paymentStatus;

    @Schema(description = "All line items that make up the bill, including the system-generated PARTICIPANT fee line, oldest first")
    private List<LineItemResponse> lineItems;

    @Schema(description = "Short-lived presigned URL to the rendered tax-invoice PDF. Present on FINAL bills "
            + "(rendered synchronously at finalize); null on drafts (previewed on the frontend) and on a "
            + "FINAL whose PDF render failed.")
    private String downloadUrl;

    /**
     * Map a stored invoice to the API shape with no line items (read paths that don't load them).
     */
    public static BillResponse fromEntity(Invoice invoice, String downloadUrl) {
        return fromEntity(invoice, List.of(), downloadUrl);
    }

    /**
     * Map a stored invoice to the API shape with its line items, attaching a freshly
     * presigned PDF URL (null when the bill has no PDF).
     */
    public static BillResponse fromEntity(Invoice invoice, List<InvoiceLineItem> lineItems, String downloadUrl) {
        return BillResponse.builder()
                .billId(invoice.getBillId())
                .status(invoice.getStatus())
                .invoiceNumber(invoice.getInvoiceNumber())
                .eventId(String.valueOf(invoice.getEventId()))
                .organizationId(invoice.getOrganizationId())
                .eventName(invoice.getEventName())
                .eventDate(invoice.getEventDate() != null ? invoice.getEventDate().toString() : null)
                .subtotal(invoice.getSubtotal())
                .taxRate(BillingRates.DEFAULT.taxRate())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .currency(BillingRates.DEFAULT.currency())
                .createdAt(invoice.getCreatedAt() != null ? invoice.getCreatedAt().toString() : null)
                .updatedAt(invoice.getUpdatedAt() != null ? invoice.getUpdatedAt().toString() : null)
                .reason(invoice.getReason())
                .paymentStatus(invoice.getPaymentStatus())
                .lineItems(lineItems.stream().map(LineItemResponse::fromEntity).toList())
                .downloadUrl(downloadUrl)
                .build();
    }
}
