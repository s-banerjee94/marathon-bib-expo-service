package com.timekeeper.bibexpo.inventory.service;

import com.timekeeper.bibexpo.inventory.exception.IdempotencyKeyReusedException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryLocationNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.StockPostingConflictException;
import com.timekeeper.bibexpo.inventory.exception.StockPostingNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.StockPostingNotReversibleException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateStockPostingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.ReverseStockPostingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.ItemActivityResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.ItemStockResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockMovementResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockPostingResponse;
import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.user.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * The only class allowed to write {@code inventory_stock} and {@code inventory_movements} —
 * always together, in one transaction, so the ledger and the balance it derives never disagree.
 *
 * <p>Every write here is a posting whose lines each post one movement per location they touch (a
 * transfer posts two) and move the balance through {@code InventoryStockRepository.deduct}/{@code add},
 * the single conditional-update pair that stops two concurrent writers from double-spending the same
 * units. A posting never overdraws a location; it is refused instead. An undo may, but only as far as
 * the shelf stood just before the posting it undoes, and a counter hand-over, posted through
 * {@link com.timekeeper.bibexpo.inventory.api.GoodieIssueRecorder}, always may.
 */
public interface StockService {

    /**
     * One page of what the organization has on hand, one entry per item with its total and how many
     * locations hold it.
     *
     * <p>Every filter is optional and they combine: free text matched against an item name, a
     * variant's attribute values or a location name; one category; one location; and whether to
     * keep only what has run low. Each one left out narrows nothing.
     *
     * <p>The search and the category only pick which items appear; the total is always the whole
     * item's. The location filter is the one that narrows the total, to what that location holds.
     * An item holding no stock at all still appears with a total of 0, unless a location filter
     * rules it out.
     *
     * <p>In name order unless the caller asks for another.
     */
    Page<StockItemResponse> listStock(Long organizationId, String search, Long categoryId, Long locationId,
                                      Boolean lowOnly, Pageable pageable, User currentUser);

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
     * One item's stock as a grid: every variant of the item, every location of the organization, the
     * balances that exist between them, and the stock actions the caller may take.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     */
    ItemStockResponse getItemStock(Long organizationId, Long itemId, User currentUser);

    /**
     * One page of an item's history, newest first, where each entry is one stock change as the person made
     * it: a posting with this item's lines under it, or a single counter hand-out. The page size counts
     * entries, not ledger lines, so a move of four sizes is one entry. Each entry says whether the caller
     * could undo it right now.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     */
    Page<ItemActivityResponse> listItemActivity(Long organizationId, Long itemId, Pageable pageable, User currentUser);

    /**
     * Posts many lines as one action, all or none. A repeat of the idempotency key answers with what that
     * key already posted and writes nothing; the same key on a different request is refused. A count
     * whose shelf no longer holds what the counter expected is refused rather than overwriting it.
     *
     * @throws InvalidUserDataException if the key is missing or a line lacks what its type needs
     * @throws com.timekeeper.bibexpo.shared.error.AccessForbiddenException if the caller's role may not take this action
     * @throws IdempotencyKeyReusedException if the key already posted a different request
     * @throws InventoryVariantNotFoundException if a variant does not belong to this organization
     * @throws InventoryLocationNotFoundException if a location does not belong to this organization
     * @throws StockPostingConflictException listing every line the stock could not take
     */
    StockPostingResponse postStock(Long organizationId, String idempotencyKey, CreateStockPostingRequest request,
                                   User currentUser);

    /**
     * Undoes a posting by writing the opposite of each of its lines as one new posting. Nothing is
     * deleted from the ledger. An organizer user may undo only their own postings from the last 24 hours.
     * An undo may take a shelf below zero only as far as it stood just before the original posting, so a
     * count that lifted an overdrawn shelf can always be undone while nothing has left that shelf since.
     *
     * @throws StockPostingNotFoundException if the posting does not belong to this organization
     * @throws StockPostingNotReversibleException if it was already undone, or is itself an undo
     * @throws StockPostingConflictException naming the first shelf that cannot take the undo
     */
    StockPostingResponse reversePosting(Long organizationId, Long postingId, String idempotencyKey,
                                        ReverseStockPostingRequest request, User currentUser);
}
