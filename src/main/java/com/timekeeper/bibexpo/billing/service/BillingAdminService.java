package com.timekeeper.bibexpo.billing.service;

import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillingSummaryResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.OrganizationBillingResponse;
import com.timekeeper.bibexpo.billing.model.entity.PaymentStatus;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.DashboardRange;
import com.timekeeper.bibexpo.model.enums.TrendInterval;
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
     * Platform-wide billing aggregates (totals, split by reason, a time trend, and top organizations).
     *
     * @param range         rolling window for the totals (ALL, YEAR or MONTH)
     * @param trendInterval bucket width for the trend (DAY, WEEK or MONTH)
     * @param trendBuckets  number of trend buckets (clamped to 1..90)
     * @param topOrgs       number of top organizations to return (clamped to 1..20)
     * @return the computed summary
     */
    BillingSummaryResponse getSummary(DashboardRange range, TrendInterval trendInterval, int trendBuckets, int topOrgs);

    /**
     * Set a bill's payment state (admin action).
     *
     * @param billId        the bill's public id
     * @param paymentStatus the new payment state
     * @return the updated bill
     * @throws com.timekeeper.bibexpo.billing.exception.BillNotFoundException if no bill has that id
     */
    BillResponse updatePaymentStatus(String billId, PaymentStatus paymentStatus);
}
