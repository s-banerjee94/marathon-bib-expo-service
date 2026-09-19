package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Access to location rows, scoped by organization.
 *
 * <p>Every read starts from the organization, and the unique key on
 * {@code (organization_id, name)} already indexes exactly that — it holds one organization's
 * locations together and in name order, so the list is a short walk with nothing left to sort. A
 * name fragment and a type are then checked against the handful of entries that walk turns up,
 * which is why neither needs an index of its own.
 */
public interface InventoryLocationRepository extends JpaRepository<InventoryLocation, Long> {

    /**
     * The organization's locations in name order, narrowed by whichever filters were given — a
     * null filter narrows nothing.
     */
    @Query("""
            SELECT l FROM InventoryLocation l
            WHERE l.organizationId = :organizationId
              AND (:name IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:typeId IS NULL OR l.typeId = :typeId)
            ORDER BY l.name
            """)
    List<InventoryLocation> search(@Param("organizationId") Long organizationId,
                                   @Param("name") String name,
                                   @Param("typeId") Long typeId);

    Optional<InventoryLocation> findByIdAndOrganizationId(Long id, Long organizationId);

    long countByTypeId(Long typeId);

    boolean existsByOrganizationIdAndName(Long organizationId, String name);
}
