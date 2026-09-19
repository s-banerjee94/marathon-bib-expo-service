package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttributeOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Access to the {@code SELECT} choice list for one attribute. Values of every other attribute
 * type are stored verbatim on the item or variant value row and never reach this table.
 */
public interface InventoryAttributeOptionRepository extends JpaRepository<InventoryAttributeOption, Long> {

    List<InventoryAttributeOption> findByAttributeId(Long attributeId);

    /** Choice lists for several attributes at once, so a list endpoint stays at two queries. */
    List<InventoryAttributeOption> findByAttributeIdIn(Collection<Long> attributeIds);

    boolean existsByAttributeIdAndValue(Long attributeId, String value);
}
