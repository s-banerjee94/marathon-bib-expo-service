package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariantAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Access to the spellings an item carries.
 *
 * <p>Every read here is answered by an index. {@code item_id} leads
 * {@code uk_inventory_variant_alias_item_value}, so an item's spellings are one seek and arrive
 * spelling order — the index supplies the sort rather than the database performing one. The
 * variant lookup rides {@code idx_inventory_variant_alias_variant}.
 *
 * <p>Note what is deliberately <b>not</b> here: an {@code IgnoreCase} spelling lookup. The column's
 * collation is case-insensitive, so the plain comparison already matches {@code M} to {@code m}
 * while still seeking the unique index; {@code UPPER(source_value) = UPPER(?)} would match the same
 * rows and scan every one of them to do it.
 */
public interface InventoryVariantAliasRepository extends JpaRepository<InventoryVariantAlias, Long> {

    List<InventoryVariantAlias> findByItemIdOrderBySourceValueAsc(Long itemId);

    Optional<InventoryVariantAlias> findByIdAndItemIdAndOrganizationId(Long id, Long itemId, Long organizationId);

    /**
     * Whether this item already reads that spelling, case-insensitively by collation.
     */
    boolean existsByItemIdAndSourceValue(Long itemId, String sourceValue);

    boolean existsByVariantId(Long variantId);

    /**
     * Clears an item's spellings. A bulk delete rather than a derived one, so the rows go without
     * loaded first.
     */
    @Modifying
    @Transactional
    @Query("delete from InventoryVariantAlias a where a.itemId = :itemId")
    int deleteByItemId(@Param("itemId") Long itemId);
}
