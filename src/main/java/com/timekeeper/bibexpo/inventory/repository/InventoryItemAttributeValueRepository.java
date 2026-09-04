package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryItemAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Access to item-attribute rows: which attributes one item uses, and &mdash; for a non-variant-defining
 * attribute &mdash; the single value that applies to the whole item.
 */
public interface InventoryItemAttributeValueRepository extends JpaRepository<InventoryItemAttributeValue, Long> {

    List<InventoryItemAttributeValue> findByItemId(Long itemId);

    Optional<InventoryItemAttributeValue> findByItemIdAndAttributeId(Long itemId, Long attributeId);

    long countByAttributeId(Long attributeId);

    long countByOptionId(Long optionId);
}
