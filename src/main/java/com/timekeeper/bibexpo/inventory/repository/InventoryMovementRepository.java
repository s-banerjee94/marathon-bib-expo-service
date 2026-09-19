package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryMovement;
import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Read access to the ledger. Rows are never updated or deleted through this repository — writes go
 * only through {@code save} from within {@code StockService}.
 *
 * <p>Every read starts from the organization the row carries, so the ledger is one table rather
 * than a hunt through variants and items to discover who owns each line. The indexes declared on
 * {@link InventoryMovement} take it from there: {@code idx_inventory_movement_org_occurred} for the
 * plain list and the time range, and {@code idx_inventory_movement_org_item_occurred} when one
 * item's history is asked for.
 */
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    /**
     * One page of an organization's ledger, narrowed by whichever filters were given — a null
     * filter narrows nothing. Narrowing happens here rather than after the fetch, so the page
     * totals describe the filtered set instead of the whole ledger.
     */
    @Query("""
            SELECT m FROM InventoryMovement m
            WHERE m.organizationId = :organizationId
              AND (:itemId IS NULL OR m.itemId = :itemId)
              AND (:variantId IS NULL OR m.variantId = :variantId)
              AND (:type IS NULL OR m.type = :type)
              AND (:reason IS NULL OR m.reason = :reason)
              AND (:performedBy IS NULL OR m.createdBy = :performedBy)
              AND (:occurredFrom IS NULL OR m.occurredAt >= :occurredFrom)
              AND (:occurredTo IS NULL OR m.occurredAt <= :occurredTo)
            """)
    Page<InventoryMovement> search(@Param("organizationId") Long organizationId,
                                   @Param("itemId") Long itemId,
                                   @Param("variantId") Long variantId,
                                   @Param("type") MovementType type,
                                   @Param("reason") MovementReason reason,
                                   @Param("performedBy") String performedBy,
                                   @Param("occurredFrom") Instant occurredFrom,
                                   @Param("occurredTo") Instant occurredTo,
                                   Pageable pageable);

    /**
     * Whether any line ever touched this location, as either end of a move. Asked before a location
     * is deleted.
     */
    // Organization first so the org index narrows the scan to one tenant's slice: the location
    // columns carry no index of their own, and one would cost every insert to serve a rare delete.
    @Query("""
            SELECT COUNT(m) > 0 FROM InventoryMovement m
            WHERE m.organizationId = :organizationId
              AND (m.fromLocationId = :locationId OR m.toLocationId = :locationId)
            """)
    boolean existsForLocation(@Param("organizationId") Long organizationId,
                              @Param("locationId") Long locationId);
}
