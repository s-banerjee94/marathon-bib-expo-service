package com.timekeeper.bibexpo.billing.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.billing.config.BillingRates;
import com.timekeeper.bibexpo.billing.exception.BillNotAllowedException;
import com.timekeeper.bibexpo.billing.exception.BillNotFoundException;
import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.AccessForbiddenException;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.OrganizationBillingResponse;
import com.timekeeper.bibexpo.billing.model.entity.Invoice;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceStatus;
import com.timekeeper.bibexpo.billing.model.entity.PaymentStatus;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.billing.repository.InvoiceRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.service.StorageService;
import com.timekeeper.bibexpo.billing.service.BillStatsTriggerService;
import com.timekeeper.bibexpo.billing.service.BillingAdminService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillingAdminServiceImpl implements BillingAdminService {

    private final InvoiceRepository invoiceRepository;
    private final OrganizationRepository organizationRepository;
    private final StorageService storageService;
    private final BillStatsTriggerService billStatsTriggerService;

    @Override
    public OrganizationBillingResponse listOrganizationBills(Long organizationId, User currentUser) {
        authorizeOrgAccess(currentUser, organizationId);
        organizationRepository.findById(organizationId).orElseThrow(OrganizationNotFoundException::new);

        List<Invoice> invoices = invoiceRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
        // Only issued (FINAL) bills are real money; drafts are still listed but never summed.
        BigDecimal totalBilled = invoices.stream()
                .filter(BillingAdminServiceImpl::isFinal)
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrganizationBillingResponse.builder()
                .organizationId(organizationId)
                .currency(BillingRates.DEFAULT.currency())
                .totalBilled(totalBilled)
                .bills(toResponses(invoices))
                .build();
    }

    @Override
    public Page<BillResponse> listAllBills(Long organizationId, Long eventId, Instant from, Instant to,
                                           String reason, PaymentStatus paymentStatus, String q, Pageable pageable) {
        Specification<Invoice> spec = buildSpec(organizationId, eventId, from, to, reason, paymentStatus, q);
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        return invoiceRepository.findAll(spec, effective)
                .map(invoice -> BillResponse.fromEntity(invoice, storageService.createDownloadUrl(invoice.getPdfKey())));
    }

    @Override
    @Transactional
    @Auditable(entityType = AuditEntityType.INVOICE, action = AuditAction.STATUS_CHANGE)
    public BillResponse updatePaymentStatus(String billId, PaymentStatus paymentStatus) {
        Invoice invoice = invoiceRepository.findByBillId(billId)
                .orElseThrow(() -> new BillNotFoundException("The bill you requested does not exist."));
        if (!isFinal(invoice)) {
            throw new BillNotAllowedException("A draft bill cannot be marked paid or unpaid.");
        }
        // Payment is one-way: a paid bill is settled for good and cannot be moved back to unpaid.
        if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
            if (paymentStatus == PaymentStatus.UNPAID) {
                throw new BillNotAllowedException("This bill has already been paid and cannot be changed.");
            }
            // Already paid and asked to stay paid — nothing changes, so no save and no recompute.
            return BillResponse.fromEntity(invoice, storageService.createDownloadUrl(invoice.getPdfKey()));
        }
        if (paymentStatus == PaymentStatus.UNPAID) {
            // Already unpaid — no change.
            return BillResponse.fromEntity(invoice, storageService.createDownloadUrl(invoice.getPdfKey()));
        }

        // The only real transition: UNPAID -> PAID. Stamp the collection date for the collected/outstanding split.
        invoice.setPaymentStatus(PaymentStatus.PAID);
        invoice.setPaidAt(Instant.now());
        Invoice saved = invoiceRepository.save(invoice);
        log.info("Bill {} marked PAID", billId);
        // The stats recompute lives in the Lambda — fire async, best-effort, never blocking the action.
        billStatsTriggerService.recomputeAsync("PAYMENT");
        return BillResponse.fromEntity(saved, storageService.createDownloadUrl(saved.getPdfKey()));
    }

    // ---- helpers ----

    /** Only issued (FINAL) bills are real money; drafts are excluded from every figure. */
    private static boolean isFinal(Invoice invoice) {
        return invoice.getStatus() == InvoiceStatus.FINAL;
    }

    private void authorizeOrgAccess(User currentUser, Long organizationId) {
        UserRole role = currentUser.getRole();
        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }
        if (currentUser.getOrganization() == null
                || !currentUser.getOrganization().getId().equals(organizationId)) {
            throw new AccessForbiddenException("You do not have access to this organization's billing.");
        }
    }

    private Specification<Invoice> buildSpec(Long organizationId, Long eventId, Instant from, Instant to,
                                             String reason, PaymentStatus paymentStatus, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organizationId"), organizationId));
            }
            if (eventId != null) {
                predicates.add(cb.equal(root.get("eventId"), eventId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (reason != null && !reason.isBlank()) {
                predicates.add(cb.equal(root.get("reason"), reason));
            }
            if (paymentStatus != null) {
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("organizerName")), like),
                        cb.like(cb.lower(root.get("eventName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<BillResponse> toResponses(List<Invoice> invoices) {
        return invoices.stream()
                .map(invoice -> BillResponse.fromEntity(invoice, storageService.createDownloadUrl(invoice.getPdfKey())))
                .toList();
    }
}
