package com.timekeeper.bibexpo.billing.service.impl;

import com.timekeeper.bibexpo.billing.exception.BillNotAllowedException;
import com.timekeeper.bibexpo.billing.exception.BillNotFoundException;
import com.timekeeper.bibexpo.billing.model.dto.request.LineItemRequest;
import com.timekeeper.bibexpo.billing.model.dto.request.ParticipantLineRequest;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.billing.model.entity.Invoice;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceLineItem;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceStatus;
import com.timekeeper.bibexpo.billing.model.entity.LineItemKind;
import com.timekeeper.bibexpo.billing.config.BillingRates;
import com.timekeeper.bibexpo.billing.repository.InvoiceLineItemRepository;
import com.timekeeper.bibexpo.billing.repository.InvoiceRepository;
import com.timekeeper.bibexpo.billing.service.BillingLineItemService;
import com.timekeeper.bibexpo.billing.service.util.BillTotalsCalculator;
import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.StorageService;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class BillingLineItemServiceImpl implements BillingLineItemService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository invoiceLineItemRepository;
    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;
    private final StorageService storageService;

    @Override
    @Auditable(entityType = AuditEntityType.INVOICE, action = AuditAction.UPDATE)
    public BillResponse addLineItem(Long eventId, String invoiceId, LineItemRequest request, User currentUser) {
        log.info("Adding {} line item to bill {} of event {} by user: {}",
                request.getKind(), invoiceId, eventId, currentUser.getUsername());
        if (request.getKind() == LineItemKind.PARTICIPANT) {
            throw new BillNotAllowedException("The participant charge cannot be added manually.");
        }
        validateLineRequest(request.getKind(), request);

        Invoice invoice = loadEditableDraft(eventId, invoiceId, currentUser);

        Instant now = Instant.now();
        InvoiceLineItem line = InvoiceLineItem.builder()
                .invoiceId(invoice.getBillId())
                .systemGenerated(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        applyRequest(line, request.getKind(), request);
        invoiceLineItemRepository.save(line);

        return touchAndRespond(invoice);
    }

    @Override
    @Auditable(entityType = AuditEntityType.INVOICE, action = AuditAction.UPDATE)
    public BillResponse updateLineItem(Long eventId, String invoiceId, Long lineItemId,
                                       LineItemRequest request, User currentUser) {
        log.info("Updating line item {} on bill {} of event {} by user: {}",
                lineItemId, invoiceId, eventId, currentUser.getUsername());

        Invoice invoice = loadEditableDraft(eventId, invoiceId, currentUser);
        InvoiceLineItem line = loadEditableLine(invoice, lineItemId);
        if (line.getKind() == LineItemKind.PARTICIPANT) {
            throw new BillNotAllowedException("The participant charge is updated through its own action.");
        }
        if (request.getKind() == LineItemKind.PARTICIPANT) {
            throw new BillNotAllowedException("A line cannot be changed into the participant charge.");
        }
        validateLineRequest(request.getKind(), request);

        applyRequest(line, request.getKind(), request);
        line.setUpdatedAt(Instant.now());
        invoiceLineItemRepository.save(line);

        return touchAndRespond(invoice);
    }

    @Override
    @Auditable(entityType = AuditEntityType.INVOICE, action = AuditAction.UPDATE)
    public BillResponse updateParticipantLine(Long eventId, String invoiceId,
                                              ParticipantLineRequest request, User currentUser) {
        log.info("Updating participant line on bill {} of event {} by user: {}",
                invoiceId, eventId, currentUser.getUsername());
        if (request.getUnitPrice() == null && request.getQuantity() == null) {
            throw new BillNotAllowedException("Provide a new unit price or quantity.");
        }

        Invoice invoice = loadEditableDraft(eventId, invoiceId, currentUser);
        InvoiceLineItem line = invoiceLineItemRepository
                .findFirstByInvoiceIdAndKind(invoice.getBillId(), LineItemKind.PARTICIPANT)
                .orElseThrow(() -> new BillNotFoundException("This bill has no participant charge to update."));

        // Partial: keep the existing unit price / quantity for whichever field is omitted.
        if (request.getUnitPrice() != null) {
            line.setUnitPrice(request.getUnitPrice());
        }
        if (request.getQuantity() != null) {
            line.setQuantity(request.getQuantity());
        }
        line.setAmount(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
        line.setUpdatedAt(Instant.now());
        invoiceLineItemRepository.save(line);

        return touchAndRespond(invoice);
    }

    @Override
    @Auditable(entityType = AuditEntityType.INVOICE, action = AuditAction.UPDATE)
    public BillResponse deleteLineItem(Long eventId, String invoiceId, Long lineItemId, User currentUser) {
        log.info("Deleting line item {} from bill {} of event {} by user: {}",
                lineItemId, invoiceId, eventId, currentUser.getUsername());

        Invoice invoice = loadEditableDraft(eventId, invoiceId, currentUser);
        InvoiceLineItem line = loadEditableLine(invoice, lineItemId);
        if (line.getKind() == LineItemKind.PARTICIPANT) {
            throw new BillNotAllowedException("The participant charge cannot be removed.");
        }

        invoiceLineItemRepository.delete(line);

        return touchAndRespond(invoice);
    }

    /** Load the bill, confirm it is under the event, the caller may access it, and it is still a draft. */
    private Invoice loadEditableDraft(Long eventId, String invoiceId, User currentUser) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new BillNotFoundException("The bill you requested does not exist."));
        if (!invoice.getEventId().equals(eventId)) {
            throw new BillNotFoundException("The bill you requested does not exist.");
        }
        Event event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
        eventAccessValidator.validateUserOrganizationAccess(currentUser, event);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BillNotAllowedException("A finalized bill can no longer be changed.");
        }
        return invoice;
    }

    /** Load a line that belongs to the bill. The PARTICIPANT line is system-generated but editable
     *  (its quantity/unit price); any other system-managed line cannot be edited by hand. */
    private InvoiceLineItem loadEditableLine(Invoice invoice, Long lineItemId) {
        InvoiceLineItem line = invoiceLineItemRepository.findById(lineItemId)
                .orElseThrow(() -> new BillNotFoundException("The line item you requested does not exist."));
        if (!line.getInvoiceId().equals(invoice.getBillId())) {
            throw new BillNotFoundException("The line item you requested does not exist.");
        }
        if (line.isSystemGenerated() && line.getKind() != LineItemKind.PARTICIPANT) {
            throw new BillNotAllowedException("This line is managed automatically and cannot be changed.");
        }
        return line;
    }

    /** A discount is a fixed amount or a percentage (exactly one); every other line needs a unit price. */
    private void validateLineRequest(LineItemKind kind, LineItemRequest request) {
        if (kind == LineItemKind.DISCOUNT) {
            boolean hasAmount = request.getAmount() != null;
            boolean hasPercent = request.getPercent() != null;
            if (hasAmount == hasPercent) {
                throw new BillNotAllowedException("A discount must be either an amount or a percentage.");
            }
        } else if (request.getUnitPrice() == null) {
            throw new BillNotAllowedException("This line needs a unit price.");
        }
    }

    /** Copy request fields onto a line. A charge's amount is quantity (default 1) × unit price; a fixed
     *  discount stores the negated amount; a percentage discount stores the percent only — its amount is
     *  re-derived against the current charge base on every recompute (see {@link #touchAndRespond}). */
    private void applyRequest(InvoiceLineItem line, LineItemKind kind, LineItemRequest request) {
        line.setKind(kind);
        line.setDescription(request.getDescription());
        if (kind == LineItemKind.DISCOUNT) {
            line.setQuantity(null);
            line.setUnitPrice(null);
            if (request.getPercent() != null) {
                line.setDiscountPercent(request.getPercent());
                line.setAmount(BigDecimal.ZERO);
            } else {
                line.setDiscountPercent(null);
                line.setAmount(request.getAmount().abs().negate());
            }
        } else {
            int quantity = request.getQuantity() != null ? request.getQuantity() : 1;
            line.setDiscountPercent(null);
            line.setQuantity(quantity);
            line.setUnitPrice(request.getUnitPrice());
            line.setAmount(request.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
        }
    }

    /**
     * Recompute the draft's totals from its current line items and return it. Totals mirror the
     * billing Lambda's authoritative recompute (percentage discounts re-derived against the charge
     * base, GST applied), so the draft stays self-consistent on every edit; the Lambda still owns
     * the final compute and PDF at finalize.
     */
    private BillResponse touchAndRespond(Invoice invoice) {
        List<InvoiceLineItem> lines = invoiceLineItemRepository.findByInvoiceIdOrderByIdAsc(invoice.getBillId());
        BillTotalsCalculator.Totals totals =
                BillTotalsCalculator.recompute(lines, BillingRates.DEFAULT.taxRate());
        invoiceLineItemRepository.saveAll(lines); // persist any re-derived percentage-discount amounts
        invoice.setSubtotal(totals.subtotal());
        invoice.setTaxAmount(totals.taxAmount());
        invoice.setTotalAmount(totals.totalAmount());
        invoice.setUpdatedAt(Instant.now());
        Invoice saved = invoiceRepository.save(invoice);
        return BillResponse.fromEntity(saved, lines, storageService.createDownloadUrl(saved.getPdfKey()));
    }
}
