package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Access to location rows, scoped by organization.
 */
public interface InventoryLocationRepository extends JpaRepository<InventoryLocation, Long> {

    List<InventoryLocation> findByOrganizationId(Long organizationId);

    Optional<InventoryLocation> findByIdAndOrganizationId(Long id, Long organizationId);

    long countByTypeId(Long typeId);

    boolean existsByOrganizationIdAndName(Long organizationId, String name);
}
