package com.timekeeper.bibexpo.billing.repository;

import com.timekeeper.bibexpo.billing.model.entity.Invoice;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read/update access to generated invoices. Rows are created only by the billing
 * Lambda; the Spring app lists them per event/organization and toggles payment
 * status. {@link JpaSpecificationExecutor} backs the filterable global feed.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, String>, JpaSpecificationExecutor<Invoice> {

    /**
     * All bills for an event, newest first.
     *
     * @param eventId the event id
     * @return matching invoices ordered by generation time descending
     */
    List<Invoice> findByEventIdOrderByCreatedAtDesc(Long eventId);

    /**
     * All bills for an organization (across its events), newest first.
     *
     * @param organizationId the organization id
     * @return matching invoices ordered by generation time descending
     */
    List<Invoice> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    /**
     * Look up a single bill by its public bill id.
     *
     * @param billId the bill's uuid
     * @return the invoice, if present
     */
    Optional<Invoice> findByBillId(String billId);

    /**
     * The event's bill in a given status (at most one draft / one final exists per event).
     * Used by finalize to find the draft it must mark FINAL before invoking the Lambda.
     *
     * @param eventId the event id
     * @param status  the status to match
     * @return the matching invoice, if present
     */
    Optional<Invoice> findFirstByEventIdAndStatus(Long eventId, InvoiceStatus status);

    /**
     * Whether the event has a bill in the given status — used to enforce "one final per
     * event" and to block reopening an event that already has a final bill.
     *
     * @param eventId the event id
     * @param status  the status to check for
     * @return true if at least one matching invoice exists
     */
    boolean existsByEventIdAndStatus(Long eventId, InvoiceStatus status);

    /**
     * Total revenue collected (paid) in a paid-at window. Counts only FINAL bills marked PAID,
     * summed by {@code paidAt}. Null bounds are unbounded. Returns 0 when nothing matches.
     *
     * @param from inclusive lower bound on paidAt, or null for unbounded
     * @param to   exclusive upper bound on paidAt, or null for unbounded
     * @return gross collected amount in the window
     */
    @Query("""
            SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i
            WHERE i.status = com.timekeeper.bibexpo.billing.model.entity.InvoiceStatus.FINAL
              AND i.paymentStatus = com.timekeeper.bibexpo.billing.model.entity.PaymentStatus.PAID
              AND i.paidAt IS NOT NULL
              AND (:from IS NULL OR i.paidAt >= :from)
              AND (:to IS NULL OR i.paidAt < :to)
            """)
    BigDecimal sumCollectedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
