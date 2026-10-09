package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Access to the fast-read stock balance table. The two conditional updates below are the only
 * legal way to change {@code onHand} — never a read-then-write in application code — so that two
 * counters posting at the same instant cannot double-spend the same units. Both clear the
 * persistence context, since a bulk update leaves any already-loaded balance holding the old
 * number. A bulk update also skips {@code AuditingEntityListener}, so both statements carry the
 * change stamps themselves — otherwise a balance would keep the stamps it was created with.
 */
public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {

    Optional<InventoryStock> findByVariantIdAndLocationId(Long variantId, Long locationId);

    List<InventoryStock> findByVariantId(Long variantId);

    /** Every balance held for any of these variants — one page of the stock list, in one query. */
    List<InventoryStock> findByVariantIdIn(List<Long> variantIds);

    /**
     * Creates the balance at zero unless the variant already has one at this location. One statement,
     * so two first postings at the same instant both end up on the same row: the second waits for the
     * first to commit and then leaves its row alone, where a read-then-insert would fail on the unique key.
     */
    @Modifying
    @Query(value = """
            INSERT INTO inventory_stock
              (variant_id, location_id, on_hand, created_at, created_by, updated_at, last_modified_by)
            VALUES (:variantId, :locationId, 0, :now, :actor, :now, :actor)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void insertIfMissing(@Param("variantId") Long variantId, @Param("locationId") Long locationId,
                         @Param("now") Instant now, @Param("actor") String actor);

    /**
     * The balance, read and locked as it stands now. A plain read would answer from the transaction's
     * snapshot, which misses a row another posting created and committed after this transaction began.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InventoryStock s WHERE s.variantId = :variantId AND s.locationId = :locationId")
    InventoryStock lockByVariantIdAndLocationId(@Param("variantId") Long variantId,
                                                @Param("locationId") Long locationId);

    @Query("SELECT COALESCE(SUM(s.onHand), 0) FROM InventoryStock s "
            + "WHERE s.variantId IN (SELECT v.id FROM InventoryVariant v WHERE v.itemId = :itemId)")
    long sumOnHandByItemId(@Param("itemId") Long itemId);

    /**
     * Deducts {@code qty} from {@code onHand} in one statement. Succeeds only if enough stock is on
     * hand, or {@code allowNegative} is true. Returns 0 rows affected when a {@code BLOCK} policy
     * shortage occurs — the caller must then throw {@code InsufficientStockException}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InventoryStock s SET s.onHand = s.onHand - :qty, s.updatedAt = :now, s.lastModifiedBy = :actor "
            + "WHERE s.id = :id AND (s.onHand >= :qty OR :allowNegative = true)")
    int deduct(@Param("id") Long id, @Param("qty") Integer qty, @Param("allowNegative") boolean allowNegative,
               @Param("now") Instant now, @Param("actor") String actor);

    /**
     * Adds {@code qty} to {@code onHand} in one statement, stamping the change onto the balance. Succeeds
     * only if the balance stays at or below 1,000,000, or {@code allowAboveCap} is true. Returns 0 rows
     * affected otherwise — the caller must then throw {@code StockCapacityExceededException}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InventoryStock s SET s.onHand = s.onHand + :qty, s.updatedAt = :now, s.lastModifiedBy = :actor "
            + "WHERE s.id = :id AND (s.onHand + :qty <= 1000000 OR :allowAboveCap = true)")
    int add(@Param("id") Long id, @Param("qty") Integer qty, @Param("allowAboveCap") boolean allowAboveCap,
            @Param("now") Instant now, @Param("actor") String actor);
}
