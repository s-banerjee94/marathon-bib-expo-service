package com.timekeeper.bibexpo.billing.service;

import com.timekeeper.bibexpo.billing.model.dto.request.LineItemRequest;
import com.timekeeper.bibexpo.billing.model.dto.request.ParticipantLineRequest;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.model.entity.User;

/**
 * Edit side of event billing: manual line items (extra charges and adjustments) on a
 * DRAFT bill. Only platform admins (ROOT/ADMIN) edit; the participant fee on the header
 * is never touched here. Each mutation only persists the line — the binding subtotal/tax/total
 * are computed by the billing Lambda at generate and finalize (the frontend previews them live
 * while editing). A FINAL bill is immutable and cannot be edited.
 */
public interface BillingLineItemService {

    /**
     * Add a manual line item to a draft bill and return the refreshed bill.
     *
     * @param eventId     the event the bill belongs to
     * @param invoiceId   the draft bill to add to
     * @param request     the line to add (a DISCOUNT amount is stored negative)
     * @param currentUser the authenticated caller
     * @return the bill with the new line and its current line items
     * @throws com.timekeeper.bibexpo.billing.exception.BillNotFoundException  if the bill does not exist under the event
     * @throws com.timekeeper.bibexpo.billing.exception.BillNotAllowedException if the bill is final
     * @throws com.timekeeper.bibexpo.exception.EventNotFoundException         if the event does not exist or is outside the caller's organization
     */
    BillResponse addLineItem(Long eventId, String invoiceId, LineItemRequest request, User currentUser);

    /**
     * Update a manual line item on a draft bill and return the refreshed bill.
     *
     * @param eventId     the event the bill belongs to
     * @param invoiceId   the draft bill the line belongs to
     * @param lineItemId  the line to update
     * @param request     the new values for the line
     * @param currentUser the authenticated caller
     * @return the bill with the updated line and its current line items
     * @throws com.timekeeper.bibexpo.billing.exception.BillNotFoundException  if the bill or line does not exist under the event
     * @throws com.timekeeper.bibexpo.billing.exception.BillNotAllowedException if the bill is final or the line is system-managed
     * @throws com.timekeeper.bibexpo.exception.EventNotFoundException         if the event does not exist or is outside the caller's organization
     */
    BillResponse updateLineItem(Long eventId, String invoiceId, Long lineItemId, LineItemRequest request, User currentUser);

    /**
     * Update the system-generated participant fee line on a draft bill — its unit price and/or
     * quantity only (its type and description are system-managed). Send only the fields to change;
     * an omitted field keeps its current value. The line amount becomes quantity × unitPrice.
     *
     * @param eventId     the event the bill belongs to
     * @param invoiceId   the draft bill whose participant line to update
     * @param request     the new unit price and/or quantity
     * @param currentUser the authenticated caller
     * @return the bill with the updated participant line and its current line items
     * @throws com.timekeeper.bibexpo.billing.exception.BillNotFoundException  if the bill or its participant line does not exist under the event
     * @throws com.timekeeper.bibexpo.billing.exception.BillNotAllowedException if the bill is final or neither field is provided
     * @throws com.timekeeper.bibexpo.exception.EventNotFoundException         if the event does not exist or is outside the caller's organization
     */
    BillResponse updateParticipantLine(Long eventId, String invoiceId, ParticipantLineRequest request, User currentUser);

    /**
     * Remove a manual line item from a draft bill and return the refreshed bill.
     *
     * @param eventId     the event the bill belongs to
     * @param invoiceId   the draft bill the line belongs to
     * @param lineItemId  the line to remove
     * @param currentUser the authenticated caller
     * @return the bill without the line and its remaining line items
     * @throws com.timekeeper.bibexpo.billing.exception.BillNotFoundException  if the bill or line does not exist under the event
     * @throws com.timekeeper.bibexpo.billing.exception.BillNotAllowedException if the bill is final or the line is system-managed
     * @throws com.timekeeper.bibexpo.exception.EventNotFoundException         if the event does not exist or is outside the caller's organization
     */
    BillResponse deleteLineItem(Long eventId, String invoiceId, Long lineItemId, User currentUser);
}
