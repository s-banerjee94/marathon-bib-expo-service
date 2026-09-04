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
 * <p>Every method takes {@code organizationId}: non-null scopes the call to one organization's own
 * terms, null scopes it to the platform defaults every organization sees, editable only through
 * {@code /api/system/inventory/terms} — {@code @PreAuthorize} on that path already restricts it to
 * {@code ROOT} before this service is reached, so no role check happens here for that case.
 */
public interface InventoryTermService {

    /**
     * Lists the terms of one kind a caller can pick from, kept in two groups: the platform
     * defaults every organization shares, and the organization's own. Each group is sorted by
     * name. The platform view fills only the defaults, since it owns no organization terms.
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
     * Renames a term. A platform default cannot be renamed through the organization-scoped path,
     * and an organization's own term cannot be renamed through the platform path — both read as
     * not found rather than forbidden.
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
