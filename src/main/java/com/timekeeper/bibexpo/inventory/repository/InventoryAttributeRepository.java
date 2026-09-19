package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Access to attribute definitions, scoped by organization the same way {@code InventoryTerm} is.
 * The list is a prefix of {@code uk_inventory_attribute_org_name}, so it seeks on organization and
 * takes its ordering from the index rather than sorting.
 */
public interface InventoryAttributeRepository extends JpaRepository<InventoryAttribute, Long> {

    /** One organization's attributes, in name order. */
    List<InventoryAttribute> findByOrganizationIdOrderByName(Long organizationId);

    boolean existsByOrganizationIdAndName(Long organizationId, String name);
}
