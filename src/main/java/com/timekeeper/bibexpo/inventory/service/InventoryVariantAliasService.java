package com.timekeeper.bibexpo.inventory.service;

import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantAliasAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantAliasNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryVariantAliasRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryVariantAliasRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryVariantAliasResponse;
import com.timekeeper.bibexpo.user.model.entity.User;

import java.util.List;

/**
 * An item's spellings: the values imported rosters use for it, and the variant each one means.
 *
 * <p>Most rosters need none at all. A cell is first matched against the item's own variant
 * values, ignoring case and surrounding spaces, so a file that already says {@code S / M / L} is
 * understood as it stands. Spellings exist for the rest — a file saying {@code M} where the item's
 * sizes read {@code 38}, or {@code Medium} where they read {@code M} — and for recording that a
 * spelling such as {@code No} in a medal column is owed nothing at all.
 *
 * <p>It belongs to the item because a spelling means what it means by virtue of which product this
 * is, so the next event using the same item inherits them rather than teaching them again.
 */
public interface InventoryVariantAliasService {

    /**
     * An item's spellings, in spelling order. They all come back at once — it has as many lines
     * as a roster has ways of spelling a size, which is a handful.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     */
    List<InventoryVariantAliasResponse> listAliases(Long organizationId, Long itemId, User currentUser);

    /**
     * Teaches the item one spelling. Leaving the variant out records that the spelling is owed
     * nothing, which is the honest reading of a blank cell.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryVariantNotFoundException if the variant is not one of this item's
     * @throws InventoryVariantAliasAlreadyExistsException if the item already reads that spelling
     */
    InventoryVariantAliasResponse createAlias(Long organizationId, Long itemId,
                                              CreateInventoryVariantAliasRequest request, User currentUser);

    /**
     * Points an existing line at a different variant, or at nothing. The spelling is the line's
     * identity and is never changed — remove the line and add it again to correct a typo.
     *
     * @throws InventoryVariantAliasNotFoundException if the line does not belong to this item
     * @throws InventoryVariantNotFoundException if the variant is not one of this item's
     */
    InventoryVariantAliasResponse updateAlias(Long organizationId, Long itemId, Long aliasId,
                                              UpdateInventoryVariantAliasRequest request, User currentUser);

    /**
     * Removes a line. The spelling simply stops being understood; nothing else changes.
     *
     * @throws InventoryVariantAliasNotFoundException if the line does not belong to this item
     */
    void deleteAlias(Long organizationId, Long itemId, Long aliasId, User currentUser);
}
