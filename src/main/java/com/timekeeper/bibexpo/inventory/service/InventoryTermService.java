package com.timekeeper.bibexpo.inventory.service;

import com.timekeeper.bibexpo.inventory.exception.InventoryTermAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermInUseException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryTermListResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryTermResponse;
import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
import com.timekeeper.bibexpo.user.model.entity.User;

/**
 * Manages the vocabulary that fills an item's category, a location's type, and an item's unit.
 *
 * <p>Every term belongs to exactly one organization. There is no shared vocabulary: an organizer
 * names their own categories, units and location types, and can rename or delete any of them,
 * because a word they cannot edit is worse than no word at all.
 */
public interface InventoryTermService {

    /**
     * This organization's terms of one kind, sorted by name.
     */
    InventoryTermListResponse listVisible(Long organizationId, TermKind kind, User currentUser);

    /**
     * Fetches one term.
     *
     * @throws InventoryTermNotFoundException if no such term exists in this scope
     */
    InventoryTermResponse getTerm(Long organizationId, Long termId, User currentUser);

    /**
     * Adds a new term.
     *
     * @throws InventoryTermAlreadyExistsException if a term of this kind with this name already exists in scope
     */
    InventoryTermResponse createTerm(Long organizationId, TermKind kind, CreateInventoryTermRequest request, User currentUser);

    /**
     * Renames a term. Another organization's term reads as not found rather than forbidden.
     *
     * @throws InventoryTermNotFoundException if no such term exists in this scope
     * @throws InventoryTermAlreadyExistsException if the new name collides with another term of the same kind in scope
     */
    InventoryTermResponse updateTerm(Long organizationId, Long termId, UpdateInventoryTermRequest request, User currentUser);

    /**
     * Deletes a term.
     *
     * @throws InventoryTermNotFoundException if no such term exists in this scope
     * @throws InventoryTermInUseException if an item or location still references this term
     */
    void deleteTerm(Long organizationId, Long termId, User currentUser);
}
