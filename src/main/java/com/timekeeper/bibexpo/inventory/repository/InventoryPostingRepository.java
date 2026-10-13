package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Access to stock postings. A posting is written once; the only later change is the undo claiming it.
 */
public interface InventoryPostingRepository extends JpaRepository<InventoryPosting, Long> {

    Optional<InventoryPosting> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<InventoryPosting> findByOrganizationIdAndCreatedByAndIdempotencyKey(Long organizationId, String createdBy,
                                                                                 String idempotencyKey);

    /**
     * Records which posting undid this one. Returns 0 when another undo already claimed it, so of two
     * undos posted at the same instant exactly one goes through.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InventoryPosting p SET p.reversedByPostingId = :reversalId "
            + "WHERE p.id = :id AND p.reversedByPostingId IS NULL")
    int markReversed(@Param("id") Long id, @Param("reversalId") Long reversalId);
}
