package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Access to attribute definitions, scoped by organization the same way {@code InventoryTerm} is.
 */
public interface InventoryAttributeRepository extends JpaRepository<InventoryAttribute, Long> {

    /** Platform defaults — {@code organizationId} is null. */
    List<InventoryAttribute> findByOrganizationIdIsNullOrderByName();

    /** Everything an organization can pick from: platform defaults plus its own attributes. */
    @Query("SELECT d FROM InventoryAttribute d WHERE "
            + "(d.organizationId = :organizationId OR d.organizationId IS NULL) ORDER BY d.name")
    List<InventoryAttribute> findVisible(@Param("organizationId") Long organizationId);

    boolean existsByOrganizationIdAndName(Long organizationId, String name);

    boolean existsByOrganizationIdIsNullAndName(String name);
}
