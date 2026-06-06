package com.timekeeper.bibexpo.billing.repository;

import com.timekeeper.bibexpo.billing.model.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Read/update access to generated invoices. Rows are created only by the billing
 * Lambda; the Spring app lists them per event/organization and toggles payment
 * status. {@link JpaSpecificationExecutor} backs the filterable global feed.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

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
     * All bills, oldest first — feeds the summary rollup (totals + trend computed in memory).
     *
     * @return every invoice ordered by generation time ascending
     */
    List<Invoice> findAllByOrderByCreatedAtAsc();
}
