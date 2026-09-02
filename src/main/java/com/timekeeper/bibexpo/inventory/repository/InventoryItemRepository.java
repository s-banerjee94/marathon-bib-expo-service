package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Access to item rows, scoped by organization.
 */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findByOrganizationId(Long organizationId);

    Optional<InventoryItem> findByIdAndOrganizationId(Long id, Long organizationId);

    long countByCategoryId(Long categoryId);

    long countByUnitId(Long unitId);

    boolean existsByOrganizationIdAndName(Long organizationId, String name);
}
