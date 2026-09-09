package com.timekeeper.bibexpo.inventory.service;

import com.timekeeper.bibexpo.inventory.exception.InventoryGoodieMappingAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryGoodieMappingNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotMappableException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryGoodieMappingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryGoodieMappingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieMappingResponse;
import com.timekeeper.bibexpo.user.model.entity.User;

import java.util.List;

/**
 * Says what each of an event's goodies columns actually is, in stock terms. The import stores a
 * goody as a column heading and a free-text cell, and knows nothing about the catalogue; this is
 * where an organizer, afterwards and at their own pace, points each heading at the item it comes
 * out of. Nothing here changes how a file is imported, and an event whose file carried no goodies
 * columns simply has nothing to link.
 *
 * <p>A link resolves to one variant on its own: an item that varies by nothing has a single
 * variant, and an item that varies by one attribute lets the participant's own cell value pick it.
 * Anything varying by more than one attribute is refused, since a single cell cannot say which
 * combination a runner is owed.</p>
 */
public interface InventoryGoodieMappingService {

    /**
     * An event's goody links, in goody-name order. The whole list comes back at once — an event
     * carries a handful of goodies, capped by its plan.
     */
    List<InventoryGoodieMappingResponse> listMappings(Long organizationId, Long eventId, User currentUser);

    /**
     * Links one goodies column to the item it is handed out from. The name must match the column
     * heading the import stored, character for character, because that is the key a participant's
     * entitlement is held under.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryItemNotMappableException if the item varies by more than one attribute
     * @throws InventoryGoodieMappingAlreadyExistsException if this goody is already linked for this event
     */
    InventoryGoodieMappingResponse createMapping(Long organizationId, Long eventId,
                                                 CreateInventoryGoodieMappingRequest request, User currentUser);

    /**
     * Points an existing link at a different item. The goody name is the link's identity and is
     * never changed — remove the link and add it again to correct a heading.
     *
     * @throws InventoryGoodieMappingNotFoundException if the link does not belong to this event
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryItemNotMappableException if the item varies by more than one attribute
     */
    InventoryGoodieMappingResponse updateMapping(Long organizationId, Long eventId, Long mappingId,
                                                 UpdateInventoryGoodieMappingRequest request, User currentUser);

    /**
     * Removes a link. Nothing else is touched: the participants keep their entitlements and the
     * item keeps its stock, the goody simply stops pointing anywhere.
     *
     * @throws InventoryGoodieMappingNotFoundException if the link does not belong to this event
     */
    void deleteMapping(Long organizationId, Long eventId, Long mappingId, User currentUser);
}
