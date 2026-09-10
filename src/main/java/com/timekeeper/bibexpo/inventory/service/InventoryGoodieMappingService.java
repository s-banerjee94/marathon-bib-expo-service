package com.timekeeper.bibexpo.inventory.service;

import com.timekeeper.bibexpo.inventory.exception.InventoryGoodieMappingAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryGoodieMappingLocationRequiredException;
import com.timekeeper.bibexpo.inventory.exception.InventoryGoodieMappingNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotMappableException;
import com.timekeeper.bibexpo.inventory.exception.InventoryLocationNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryGoodieMappingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryGoodieMappingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieMappingResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieResolutionResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieShortfallResponse;
import com.timekeeper.bibexpo.user.model.entity.User;

import java.util.List;

/**
 * Says what each of an event's goodies columns actually is, in stock terms. The import stores a
 * goody as a column heading and a free-text cell, and knows nothing about the catalogue; this is
 * where an organizer, afterwards and at their own pace, points each heading at the item it comes
 * out of. Nothing here changes how a file is imported, and an event whose file carried no goodies
 * columns simply has nothing to link.
 *
 * <p>A link also carries the location the goody is handed out from, which is the stock a handover
 * will deduct. It may be left open while the event is a draft, because the item is known as soon as
 * the roster is imported while the counter is often chosen days before the expo, but an event
 * cannot be published while any of its links is still missing one.</p>
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
     * Links one goodies column to the item it is handed out from, and optionally to the location
     * it leaves. The name must match the column heading the import stored, character for character,
     * because that is the key a participant's entitlement is held under.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryItemNotMappableException if the item varies by more than one attribute
     * @throws InventoryGoodieMappingAlreadyExistsException if this goody is already linked for this event
     * @throws InventoryLocationNotFoundException if the location does not belong to this organization
     * @throws InventoryGoodieMappingLocationRequiredException if no location was given and the event
     *         is already published
     */
    InventoryGoodieMappingResponse createMapping(Long organizationId, Long eventId,
                                                 CreateInventoryGoodieMappingRequest request, User currentUser);

    /**
     * Points an existing link at a different item or location. The goody name is the link's
     * identity and is never changed — remove the link and add it again to correct a heading.
     *
     * @throws InventoryGoodieMappingNotFoundException if the link does not belong to this event
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryItemNotMappableException if the item varies by more than one attribute
     * @throws InventoryLocationNotFoundException if the location does not belong to this organization
     * @throws InventoryGoodieMappingLocationRequiredException if the location was cleared and the
     *         event is already published
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

    /**
     * The check screen: every goody the event's roster carries, every distinct spelling under it,
     * and what each spelling resolves to. This is where an organizer sees that 812 runners asked
     * for {@code M} and that nothing yet knows what {@code M} means.
     *
     * <p>A spelling is read in a fixed order and never guessed: the item's own variant values
     * first, then its taught spellings, then the item itself when it varies by nothing. Whatever none of
     * those recognise is reported as needing attention, with the number of participants behind it,
     * so the organizer can teach one spelling and clear a whole column.
     *
     * <p>Both sides come from a read that is already paid for — one query for the event's links and
     * one counter read for the demand — so the roster itself is never walked.
     */
    List<InventoryGoodieResolutionResponse> resolveGoodies(Long organizationId, Long eventId, User currentUser);

    /**
     * The shortfall report: for each goody, how many of each variant the roster asks for, and how
     * many are on the shelf it is handed out from.
     *
     * <p>The two halves are answered months apart and the report is useful with only the first.
     * Demand can be totalled as soon as the roster is imported, before anything is ordered and
     * before a location exists, and that column alone is the purchase order. On hand and short
     * fill in once a location is chosen and stock has arrived.
     *
     * <p>Demand is what the roster promised, never what turnout is expected to be: every
     * registered participant owed a goody is counted, because every one of them may walk in.
     * Spellings nothing recognises are counted apart in {@code unresolvedParticipants} rather than
     * guessed into a variant, so a shortfall is never inflated or hidden by them.
     *
     * <p>Each goody also reports what sits at the organization's other locations, since a shortfall
     * covered from another shelf is a transfer rather than a purchase.
     */
    List<InventoryGoodieShortfallResponse> shortfall(Long organizationId, Long eventId, User currentUser);
}
