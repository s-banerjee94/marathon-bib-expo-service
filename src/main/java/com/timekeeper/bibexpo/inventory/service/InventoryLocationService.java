package com.timekeeper.bibexpo.inventory.service;

import com.timekeeper.bibexpo.inventory.exception.InventoryLocationAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryLocationNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryLocationRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryLocationRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryLocationResponse;
import com.timekeeper.bibexpo.user.model.entity.User;

import java.util.List;

/**
 * Manages where an organization's stock physically sits. Locations are never deleted through the
 * API — once one exists it may carry ledger history, so it can only be renamed or retyped.
 */
public interface InventoryLocationService {

    /** Every location the organization owns. */
    List<InventoryLocationResponse> listLocations(Long organizationId, User currentUser);

    /**
     * Adds a new location.
     *
     * @throws InventoryTermNotFoundException if {@code request.typeId} is not a location-type term visible to this organization
     * @throws InventoryLocationAlreadyExistsException if a location with this name already exists
     */
    InventoryLocationResponse createLocation(Long organizationId, CreateInventoryLocationRequest request, User currentUser);

    /**
     * Renames a location and/or changes its type. Fields left out of the request are unchanged.
     *
     * @throws InventoryLocationNotFoundException if the location does not belong to this organization
     * @throws InventoryTermNotFoundException if a new {@code typeId} is not a location-type term visible to this organization
     * @throws InventoryLocationAlreadyExistsException if the new name collides with another location
     */
    InventoryLocationResponse updateLocation(Long organizationId, Long locationId,
                                              UpdateInventoryLocationRequest request, User currentUser);
}
