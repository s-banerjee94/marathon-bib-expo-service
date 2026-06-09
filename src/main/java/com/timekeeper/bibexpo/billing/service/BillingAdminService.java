package com.timekeeper.bibexpo.billing.service;

import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.OrganizationBillingResponse;
import com.timekeeper.bibexpo.billing.model.entity.PaymentStatus;
import com.timekeeper.bibexpo.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Cross-event billing reads for organization and platform admins. Bills are produced
 * only by the billing Lambda; this service never computes a charge. It rolls them up
 * (per organization, globally, and as aggregates) and flips a bill's payment state.
 */
public interface BillingAdminService {

    /**
     * List every bill for one organization, newest first, with the rolled-up total.
     *
     * @param organizationId the organization whose bills to list
     * @param currentUser    the authenticated caller (ORGANIZER_ADMIN limited to their own organization)
     * @return the organization's bills plus the summed total
     * @throws com.timekeeper.bibexpo.exception.UnauthorizedAccessException if an ORGANIZER_ADMIN requests another organization
     * @throws com.timekeeper.bibexpo.exception.OrganizationNotFoundException if the organization does not exist
     */
    OrganizationBillingResponse listOrganizationBills(Long organizationId, User currentUser);

    /**
     * The global, filterable bill feed for platform admins.
     *
     * @param organizationId optional organization filter
     * @param eventId        optional event filter
     * @param from           optional inclusive lower bound on generation time
     * @param to             optional inclusive upper bound on generation time
     * @param reason         optional trigger filter (AUTO or MANUAL)
     * @param paymentStatus  optional payment-state filter
     * @param q              optional free-text match on organizer name or event name
     * @param pageable       page, size and sort (defaults to createdAt descending)
     * @return a page of bills matching the filters
     */
    Page<BillResponse> listAllBills(Long organizationId, Long eventId, Instant from, Instant to,
                                    String reason, PaymentStatus paymentStatus, String q, Pageable pageable);

    /**
     * Mark a bill paid (admin action). Payment is one-way: only a FINAL bill can be paid, and once
     * paid it cannot be reverted to unpaid. The UNPAID-&gt;PAID transition stamps the collection date
     * and triggers an asynchronous bill-stats recompute; a request that does not change the state is
     * a no-op.
     *
     * @param billId        the bill's public id
     * @param paymentStatus the requested payment state
     * @return the updated bill (or the unchanged bill for a no-op)
     * @throws com.timekeeper.bibexpo.billing.exception.BillNotFoundException  if no bill has that id
     * @throws com.timekeeper.bibexpo.billing.exception.BillNotAllowedException if the bill is a draft, or is already paid and a revert to unpaid is requested
     */
    BillResponse updatePaymentStatus(String billId, PaymentStatus paymentStatus);
}
