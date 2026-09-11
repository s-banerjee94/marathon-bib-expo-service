package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryGoodieMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Access to the goody-to-item mappings an event holds. Every read is scoped by event, which leads
 * the unique constraint, so the list costs a single seek.
 */
public interface InventoryGoodieMappingRepository extends JpaRepository<InventoryGoodieMapping, Long> {

    List<InventoryGoodieMapping> findByEventIdOrderByGoodieNameAsc(Long eventId);

    Optional<InventoryGoodieMapping> findByIdAndEventIdAndOrganizationId(Long id, Long eventId, Long organizationId);

    Optional<InventoryGoodieMapping> findByEventIdAndGoodieName(Long eventId, String goodieName);

    boolean existsByEventIdAndGoodieName(Long eventId, String goodieName);

    boolean existsByItemId(Long itemId);

    boolean existsByLocationId(Long locationId);

    /**
     * Whether the event still has a linked goody with nowhere to hand it out from — the one
     * question that holds an event in draft on inventory's account.
     */
    boolean existsByEventIdAndLocationIdIsNull(Long eventId);

    /**
     * Clears an event's mappings. The event id is a plain column with no association behind it, so
     * nothing removes these rows when the event goes.
     */
    @Modifying
    @Transactional
    @Query("delete from InventoryGoodieMapping m where m.eventId = :eventId")
    int deleteByEventId(@Param("eventId") Long eventId);
}
