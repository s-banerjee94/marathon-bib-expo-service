package com.timekeeper.bibexpo.inventory.service;

import com.timekeeper.bibexpo.inventory.exception.InventoryLocationAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryLocationInUseException;
import com.timekeeper.bibexpo.inventory.exception.InventoryLocationLimitReachedException;
import com.timekeeper.bibexpo.inventory.exception.InventoryLocationNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryLocationRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryLocationRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryLocationResponse;
import com.timekeeper.bibexpo.user.model.entity.User;

import java.util.List;

/**
 * Manages where an organization's stock physically sits. A location that has carried stock is kept
 * for good, since the ledger points at it; one that never has is only a typo and can be removed.
 */
public interface InventoryLocationService {

    /**
     * The organization's locations in name order, narrowed by whichever filters were given: part
     * of a name, matched anywhere and ignoring case, and one location type. Each one left out
     * narrows nothing. The whole list comes back at once — an organization is capped at a number
     * of locations a single response holds comfortably.
     */
    List<InventoryLocationResponse> listLocations(Long organizationId, String name, Long typeId, User currentUser);

    /**
     * Adds a new location.
     *
     * @throws InventoryTermNotFoundException if {@code request.typeId} is not a location-type term visible to this organization
     * @throws InventoryLocationAlreadyExistsException if a location with this name already exists
     * @throws InventoryLocationLimitReachedException if the organization already holds as many locations as its plan allows
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

    /**
     * Removes a location no ledger line has ever touched. Once stock has moved in or out of a
     * location the ledger names it forever, so it stays.
     *
     * @throws InventoryLocationNotFoundException if the location does not belong to this organization
     * @throws InventoryLocationInUseException if any movement was ever posted at this location
     */
    void deleteLocation(Long organizationId, Long locationId, User currentUser);
}
