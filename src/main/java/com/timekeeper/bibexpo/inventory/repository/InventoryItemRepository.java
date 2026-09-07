package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Access to item rows, scoped by organization. Paging is one query, and the two indexes declared
 * on {@link InventoryItem} let the database walk straight to the page instead of sorting the
 * catalogue: {@code idx_inventory_item_org_created} for the plain list and the date range,
 * {@code idx_inventory_item_org_category_created} when a category narrows it.
 *
 * <p>The one shape no index can serve is the name search, because a fragment matched anywhere in
 * the name has no starting point to seek to. It reads the organization's index entries and keeps
 * what matches, which stays cheap while a catalogue is thousands of items rather than millions.
 */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    /**
     * One page of an organization's items, narrowed by whichever filters were given — a null
     * filter narrows nothing. Narrowing happens here rather than after the fetch, so the page
     * totals describe the filtered set instead of the whole catalogue.
     */
    @Query("""
            SELECT i FROM InventoryItem i
            WHERE i.organizationId = :organizationId
              AND (:name IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:categoryId IS NULL OR i.categoryId = :categoryId)
              AND (:createdFrom IS NULL OR i.createdAt >= :createdFrom)
              AND (:createdTo IS NULL OR i.createdAt <= :createdTo)
            """)
    Page<InventoryItem> search(@Param("organizationId") Long organizationId,
                               @Param("name") String name,
                               @Param("categoryId") Long categoryId,
                               @Param("createdFrom") Instant createdFrom,
                               @Param("createdTo") Instant createdTo,
                               Pageable pageable);

    Optional<InventoryItem> findByIdAndOrganizationId(Long id, Long organizationId);

    long countByCategoryId(Long categoryId);

    long countByUnitId(Long unitId);

    boolean existsByOrganizationIdAndName(Long organizationId, String name);
}
