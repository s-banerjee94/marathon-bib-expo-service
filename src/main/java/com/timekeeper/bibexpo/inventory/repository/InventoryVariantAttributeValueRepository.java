package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariantAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Access to variant-attribute-value rows: which value one variant carries, for each
 * variant-defining attribute it is composed of.
 */
public interface InventoryVariantAttributeValueRepository extends JpaRepository<InventoryVariantAttributeValue, Long> {

    List<InventoryVariantAttributeValue> findByVariantId(Long variantId);

    List<InventoryVariantAttributeValue> findByVariantIdIn(List<Long> variantIds);

    long countByAttributeId(Long attributeId);

    long countByOptionId(Long optionId);
}
