package com.timekeeper.bibexpo.inventory.repository;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryTerm;
import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Access to term (vocabulary) rows. Every term belongs to exactly one organization, so both
 * lookups are a prefix of {@code uk_inventory_term_kind_org_name}: the list seeks on kind and
 * organization and takes its ordering from the index rather than sorting.
 */
public interface InventoryTermRepository extends JpaRepository<InventoryTerm, Long> {

    /** One organization's terms for a kind, in name order. */
    List<InventoryTerm> findByKindAndOrganizationIdOrderByName(TermKind kind, Long organizationId);

    boolean existsByKindAndOrganizationIdAndName(TermKind kind, Long organizationId, String name);
}
