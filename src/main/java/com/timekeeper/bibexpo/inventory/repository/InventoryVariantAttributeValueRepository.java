package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariantAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Access to variant-attribute-value rows: which value one variant carries, for each
 * variant-defining attribute it is composed of.
 */
public interface InventoryVariantAttributeValueRepository extends JpaRepository<InventoryVariantAttributeValue, Long> {

    List<InventoryVariantAttributeValue> findByVariantId(Long variantId);

    List<InventoryVariantAttributeValue> findByVariantIdIn(List<Long> variantIds);

    /**
     * The distinct attributes an item's variants are composed of — in other words, how many
     * dimensions the item varies by. Empty for an item whose single variant carries no values.
     */
    @Query("""
            SELECT DISTINCT av.attributeId FROM InventoryVariantAttributeValue av
            WHERE av.variantId IN (SELECT v.id FROM InventoryVariant v WHERE v.itemId = :itemId)
            """)
    List<Long> findDistinctAttributeIdsByItemId(@Param("itemId") Long itemId);

    long countByAttributeId(Long attributeId);

    long countByOptionId(Long optionId);
}
