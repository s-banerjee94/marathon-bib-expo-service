package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Access to variant rows, scoped by their owning item.
 */
public interface InventoryVariantRepository extends JpaRepository<InventoryVariant, Long> {

    List<InventoryVariant> findByItemId(Long itemId);

    List<InventoryVariant> findByItemIdIn(List<Long> itemIds);
}
