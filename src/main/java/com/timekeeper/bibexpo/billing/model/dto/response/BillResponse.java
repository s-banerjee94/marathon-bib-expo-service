package com.timekeeper.bibexpo.billing.model.dto.response;

import com.timekeeper.bibexpo.billing.model.entity.Invoice;
import com.timekeeper.bibexpo.billing.model.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One generated bill for an event (immutable snapshot) with a short-lived PDF link")
public class BillResponse {

    @Schema(description = "Unique bill identifier", example = "9f1c2e3a-4b5d-6e7f-8a9b-0c1d2e3f4a5b")
    private String billId;

    @Schema(description = "Event ID this bill belongs to", example = "42")
    private String eventId;

    @Schema(description = "Organization that was billed", example = "7")
    private Long organizationId;

    @Schema(description = "Event name captured when the bill was generated", example = "Mumbai Marathon 2026")
    private String eventName;

    @Schema(description = "Event start date captured at generation time (ISO-8601 instant)", example = "2026-01-19T01:30:00Z")
    private String eventDate;

    @Schema(description = "Total uploaded participants charged", example = "1200")
    private Long participantCount;

    @Schema(description = "Per-participant price", example = "5.00")
    private BigDecimal unitPrice;

    @Schema(description = "Charge before tax", example = "6000.00")
    private BigDecimal subtotal;

    @Schema(description = "GST rate applied (percent)", example = "18")
    private BigDecimal taxRate;

    @Schema(description = "GST amount", example = "1080.00")
    private BigDecimal taxAmount;

    @Schema(description = "Total payable (subtotal + tax)", example = "7080.00")
    private BigDecimal totalAmount;

    @Schema(description = "Currency code", example = "INR")
    private String currency;

    @Schema(description = "When the bill was generated (ISO-8601 instant)", example = "2026-01-19T07:00:00Z")
    private String createdAt;

    @Schema(description = "What triggered it — AUTO (5h post-completion timer) or MANUAL (on-demand)", example = "AUTO")
    private String reason;

    @Schema(description = "Payment state, set manually by an admin (defaults to UNPAID at generation)", example = "UNPAID")
    private PaymentStatus paymentStatus;

    @Schema(description = "Short-lived presigned URL to download the invoice PDF")
    private String downloadUrl;

    /**
     * Map a stored invoice to the API shape, attaching a freshly presigned PDF URL.
     */
    public static BillResponse fromEntity(Invoice invoice, String downloadUrl) {
        return BillResponse.builder()
                .billId(invoice.getBillId())
                .eventId(String.valueOf(invoice.getEventId()))
                .organizationId(invoice.getOrganizationId())
                .eventName(invoice.getEventName())
                .eventDate(invoice.getEventDate() != null ? invoice.getEventDate().toString() : null)
                .participantCount(invoice.getParticipantCount())
                .unitPrice(invoice.getUnitPrice())
                .subtotal(invoice.getSubtotal())
                .taxRate(invoice.getTaxRate())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .currency(invoice.getCurrency())
                .createdAt(invoice.getCreatedAt() != null ? invoice.getCreatedAt().toString() : null)
                .reason(invoice.getReason())
                .paymentStatus(invoice.getPaymentStatus())
                .downloadUrl(downloadUrl)
                .build();
    }
}
