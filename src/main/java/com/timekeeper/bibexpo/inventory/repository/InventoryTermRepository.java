package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryTerm;
import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Access to term (vocabulary) rows, scoped by kind and organization.
 */
public interface InventoryTermRepository extends JpaRepository<InventoryTerm, Long> {

    /** Platform defaults for a kind — {@code organizationId} is null. */
    List<InventoryTerm> findByKindAndOrganizationIdIsNullOrderByName(TermKind kind);

    /** One organization's own terms for a kind. */
    List<InventoryTerm> findByKindAndOrganizationIdOrderByName(TermKind kind, Long organizationId);

    /** Everything an organization can pick from for a kind: platform defaults plus its own terms. */
    @Query("SELECT t FROM InventoryTerm t WHERE t.kind = :kind "
            + "AND (t.organizationId = :organizationId OR t.organizationId IS NULL) "
            + "ORDER BY t.name")
    List<InventoryTerm> findVisible(@Param("kind") TermKind kind, @Param("organizationId") Long organizationId);

    boolean existsByKindAndOrganizationIdAndName(TermKind kind, Long organizationId, String name);

    boolean existsByKindAndOrganizationIdIsNullAndName(TermKind kind, String name);
}
