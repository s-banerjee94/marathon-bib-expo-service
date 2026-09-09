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
 * <p>Every attribute belongs to exactly one organization. There is no shared list: an organizer
 * defines Size, Colour and anything else their own products need, and owns every value on it —
 * a Colour they cannot add Teal to would be no use to them.
 */
public interface InventoryAttributeService {

    /**
     * This organization's attributes, sorted by name.
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
