package com.timekeeper.bibexpo.inventory.service;

import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeInUseException;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeOptionAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeOptionInUseException;
import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeOptionNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryAttributeRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryAttributeOptionRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryAttributeRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryAttributeOptionRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryAttributeListResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryAttributeResponse;
import com.timekeeper.bibexpo.user.model.entity.User;

/**
 * Manages the reusable attributes that fill an item's variant-defining and informational
 * dimensions, and the allowed choice list for each {@code SELECT}-type one.
 *
 * <p>Every method takes {@code organizationId}: non-null scopes the call to one organization's own
 * attributes, null scopes it to the platform defaults every organization sees, editable only through
 * {@code /api/system/inventory/attributes} — {@code @PreAuthorize} on that path already restricts it
 * to {@code ROOT} before this service is reached, so no role check happens here for that case.
 */
public interface InventoryAttributeService {

    /**
     * Every attribute a caller can pick from, kept in two groups: the platform defaults every
     * organization shares, and the organization's own. Each group is sorted by name. The platform
     * view fills only the defaults, since it owns no organization attributes.
     */
    InventoryAttributeListResponse listVisible(Long organizationId, User currentUser);

    InventoryAttributeResponse getAttribute(Long organizationId, Long attributeId, User currentUser);

    /**
     * Adds a new attribute.
     *
     * @throws InventoryAttributeAlreadyExistsException if a attribute with this name already exists in scope
     */
    InventoryAttributeResponse createAttribute(Long organizationId,
                                                            CreateInventoryAttributeRequest request,
                                                            User currentUser);

    /**
     * Renames a attribute and/or changes whether it is required. {@code type} and
     * {@code variantAttribute} cannot be changed after creation.
     *
     * @throws InventoryAttributeNotFoundException if no such attribute exists in this scope
     * @throws InventoryAttributeAlreadyExistsException if the new name collides with another attribute in scope
     */
    InventoryAttributeResponse updateAttribute(Long organizationId, Long attributeId,
                                                            UpdateInventoryAttributeRequest request,
                                                            User currentUser);

    /**
     * Deletes a attribute and its values.
     *
     * @throws InventoryAttributeNotFoundException if no such attribute exists in this scope
     * @throws InventoryAttributeInUseException if any item or variant still references it
     */
    void deleteAttribute(Long organizationId, Long attributeId, User currentUser);

    /**
     * Adds an allowed choice to a {@code SELECT} attribute.
     *
     * @throws InventoryAttributeNotFoundException if no such attribute exists in this scope
     * @throws InventoryAttributeOptionAlreadyExistsException if this value already exists for the attribute
     */
    InventoryAttributeResponse addOption(Long organizationId, Long attributeId,
                                                    CreateInventoryAttributeOptionRequest request, User currentUser);

    /**
     * Renames an existing value.
     *
     * @throws InventoryAttributeOptionNotFoundException if the value does not belong to this attribute
     * @throws InventoryAttributeOptionAlreadyExistsException if the new value collides with another on the attribute
     */
    InventoryAttributeResponse updateOption(Long organizationId, Long attributeId, Long optionId,
                                                       UpdateInventoryAttributeOptionRequest request, User currentUser);

    /**
     * Removes a value.
     *
     * @throws InventoryAttributeOptionNotFoundException if the value does not belong to this attribute
     * @throws InventoryAttributeOptionInUseException if any item or variant still carries this value
     */
    InventoryAttributeResponse removeOption(Long organizationId, Long attributeId, Long optionId,
                                                       User currentUser);
}
