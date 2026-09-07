package com.timekeeper.bibexpo.inventory.service;

import com.timekeeper.bibexpo.inventory.exception.InsufficientStockException;
import com.timekeeper.bibexpo.inventory.exception.InventoryLocationNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.AdjustStockRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.ReceiveStockRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.TransferStockRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockBalanceResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockMovementResponse;
import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.user.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

/**
 * The only class allowed to write {@code inventory_stock} and {@code inventory_movements} —
 * always together, in one transaction, so the ledger and the balance it derives never disagree.
 *
 * <p>Every write here posts exactly one new movement per location it touches (a transfer posts
 * two) and moves the balance through {@code InventoryStockRepository.deduct}/{@code add}, the
 * single conditional-update pair that stops two concurrent writers from double-spending the same
 * units. A shortage under the {@code BLOCK} policy used here never overdraws a location — it
 * throws instead.
 */
public interface StockService {

    /**
     * One page of what the organization has on hand, one entry per item, carrying the
     * variant-and-location rows behind its total.
     *
     * <p>Every filter is optional and they combine: free text matched against an item name, a
     * variant's attribute values or a location name; one location; and whether to keep only what
     * has run low. Each one left out narrows nothing.
     *
     * <p>A search or a location also narrows the rows under each item, and the item's total then
     * counts only the rows still shown — a filtered list totals what it displays. An item holding
     * no stock at all still appears, with no rows, unless a location filter rules it out.
     *
     * <p>In name order unless the caller asks for another.
     */
    Page<StockItemResponse> listStock(Long organizationId, String search, Long locationId, Boolean lowOnly,
                                      Pageable pageable, User currentUser);

    /**
     * One page of the organization's ledger.
     *
     * <p>Every filter is optional and they combine: one item or one of its variants, one kind of
     * movement, one reason, the username of whoever posted it, and a closed range over the moment
     * the stock actually moved. Each one left out narrows nothing.
     *
     * <p>Newest first — by {@code occurredAt}, the moment of the movement itself, not the moment
     * the row was saved — unless the caller asks for another order.
     *
     * @throws InvalidUserDataException if the range starts after it ends
     */
    Page<StockMovementResponse> listMovements(Long organizationId, Long itemId, Long variantId,
                                              MovementType type, MovementReason reason, String performedBy,
                                              Instant occurredFrom, Instant occurredTo,
                                              Pageable pageable, User currentUser);

    /**
     * Records stock arriving at a location from outside the system — an opening balance or a
     * manual receipt. Always succeeds; a receipt can never be short.
     *
     * @throws InventoryVariantNotFoundException if the variant does not belong to this organization
     * @throws InventoryLocationNotFoundException if the location does not belong to this organization
     */
    StockBalanceResponse receiveStock(Long organizationId, ReceiveStockRequest request, User currentUser);

    /**
     * Moves stock from one location to another. Posts two ledger lines, one per location, and
     * returns the resulting balance at each — source first, then destination.
     *
     * @throws InsufficientStockException if the source location does not hold enough stock
     */
    List<StockBalanceResponse> transferStock(Long organizationId, TransferStockRequest request, User currentUser);

    /**
     * Corrects the counted quantity at a location. {@code DAMAGED} and {@code LOST} always remove
     * stock, whatever sign the quantity carries; {@code CORRECTION} follows the sign. An adjustment
     * can never take the balance below zero.
     *
     * @throws InvalidUserDataException if the quantity is zero, or the reason does not explain a recount
     * @throws InsufficientStockException if the adjustment removes more than is on hand
     */
    StockBalanceResponse adjustStock(Long organizationId, AdjustStockRequest request, User currentUser);
}
