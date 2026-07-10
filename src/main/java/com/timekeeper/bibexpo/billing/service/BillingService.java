package com.timekeeper.bibexpo.billing.service;

import com.timekeeper.bibexpo.billing.model.dto.response.BillGenerationResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.billing.model.entity.InvoiceStatus;
import com.timekeeper.bibexpo.model.entity.User;

import java.util.List;

/**
 * Read and on-demand-trigger side of event billing. Bills are computed and written
 * solely by the external billing Lambda; this service never calculates a charge or
 * renders a PDF. It only lists the bills the Lambda has produced and asks the Lambda
 * to produce one now.
 */
public interface BillingService {

    /**
     * List every bill generated for an event, newest first, each carrying a freshly
     * presigned short-lived URL to its PDF.
     *
     * @param eventId     the event whose bills to list
     * @param currentUser the authenticated caller (must own the event's organization)
     * @return the event's bills, newest first (empty if none)
     * @throws com.timekeeper.bibexpo.exception.EventNotFoundException     if the event does not exist or is outside the caller's organization
     * @throws com.timekeeper.bibexpo.exception.AccessForbiddenException if the caller has no organization
     */
    List<BillResponse> listBills(Long eventId, User currentUser);

    /**
     * Request an on-demand bill for a terminal (completed/cancelled) event by invoking
     * the billing Lambda directly. A draft is a replaceable proforma; a final is the
     * issued, numbered, immutable tax invoice that closes the event to further billing.
     * Organizer admins always get a draft; only platform admins may request a final.
     * Each request spends one of the caller's per-event manual-request slots.
     *
     * @param eventId     the event to bill
     * @param mode        the requested bill type (draft or final); coerced to draft for non-admins
     * @param currentUser the authenticated caller (must own the event's organization)
     * @return the Lambda's outcome plus the event's refreshed bill list
     * @throws com.timekeeper.bibexpo.exception.EventNotFoundException     if the event does not exist or is outside the caller's organization
     * @throws com.timekeeper.bibexpo.billing.exception.BillNotAllowedException    if the event is not terminal, the quota is spent, or a final already exists
     * @throws com.timekeeper.bibexpo.billing.exception.BillGenerationException    if the Lambda invocation fails
     */
    BillGenerationResponse generateBill(Long eventId, InvoiceStatus mode, User currentUser);
}
