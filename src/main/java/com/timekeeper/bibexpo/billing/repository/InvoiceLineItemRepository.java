package com.timekeeper.bibexpo.billing.repository;

import com.timekeeper.bibexpo.billing.model.entity.InvoiceLineItem;
import com.timekeeper.bibexpo.billing.model.entity.LineItemKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Access to an invoice's {@link InvoiceLineItem} rows — the extra charges and
 * adjustments beyond the header's participant fee.
 */
public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, Long> {

    /**
     * All line items for an invoice, in creation order.
     *
     * @param invoiceId the parent invoice
     * @return the invoice's line items, oldest first (empty if none)
     */
    List<InvoiceLineItem> findByInvoiceIdOrderByIdAsc(String invoiceId);

    /**
     * All line items for several invoices at once, for batch read paths (e.g. listing an
     * event's bills) that would otherwise issue one query per invoice.
     *
     * @param invoiceIds the parent invoices
     * @return the matching line items (empty if none)
     */
    List<InvoiceLineItem> findByInvoiceIdInOrderByIdAsc(List<String> invoiceIds);

    /**
     * The invoice's single line of a given kind — used to load the system-generated PARTICIPANT
     * fee line for in-place edits.
     *
     * @param invoiceId the parent invoice
     * @param kind      the line kind to match
     * @return the matching line, if present
     */
    Optional<InvoiceLineItem> findFirstByInvoiceIdAndKind(String invoiceId, LineItemKind kind);
}
