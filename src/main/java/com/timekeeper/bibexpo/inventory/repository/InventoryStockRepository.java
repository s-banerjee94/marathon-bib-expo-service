package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryStock;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Deducts {@code qty} from {@code onHand} in one statement. Succeeds only if enough stock is on
     * hand, or {@code allowNegative} is true. Returns 0 rows affected when a {@code BLOCK} policy
     * shortage occurs — the caller must then throw {@code InsufficientStockException}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InventoryStock s SET s.onHand = s.onHand - :qty, s.updatedAt = :now, s.lastModifiedBy = :actor "
            + "WHERE s.id = :id AND (s.onHand >= :qty OR :allowNegative = true)")
    int deduct(@Param("id") Long id, @Param("qty") Integer qty, @Param("allowNegative") boolean allowNegative,
               @Param("now") Instant now, @Param("actor") String actor);

    /** Adds {@code qty} to {@code onHand} in one statement, stamping the change onto the balance. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InventoryStock s SET s.onHand = s.onHand + :qty, s.updatedAt = :now, s.lastModifiedBy = :actor "
            + "WHERE s.id = :id")
    int add(@Param("id") Long id, @Param("qty") Integer qty, @Param("now") Instant now,
            @Param("actor") String actor);
}
