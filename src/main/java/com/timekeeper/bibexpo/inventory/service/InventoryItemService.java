package com.timekeeper.bibexpo.inventory.service;

import com.timekeeper.bibexpo.inventory.exception.InventoryItemAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemInUseException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemLimitReachedException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemLinkedToGoodieException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryTermNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantAliasedException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantInUseException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantNotFoundException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryItemRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryVariantRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryItemRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryItemSummaryResponse;
import com.timekeeper.bibexpo.storage.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.user.model.entity.User;

import java.util.List;

/**
 * Manages items (the things an organization stocks) and their variants (size, colour, flavour).
 * An item always carries at least one variant — {@code DEFAULT} when none is given — so every
 * stock-writing path always has a variant to work with.
 *
 * <p>Organization-scoped only.
 */
public interface InventoryItemService {

    /** Every item of the organization as id and name, in name order. The item quota bounds the list. */
    List<InventoryItemSummaryResponse> listItems(Long organizationId, User currentUser);

    /** One item with its attributes, its variants and their attribute values. */
    InventoryItemResponse getItem(Long organizationId, Long itemId, User currentUser);

    /**
     * Adds a new item.
     *
     * @throws InventoryTermNotFoundException if the category or unit term is not visible to this organization
     * @throws InventoryItemAlreadyExistsException if an item with this name already exists
     * @throws InventoryItemLimitReachedException if the organization already holds as many items as its plan allows
     */
    InventoryItemResponse createItem(Long organizationId, CreateInventoryItemRequest request, User currentUser);

    /**
     * Updates an item's name, category, unit, low-stock threshold, or note. Fields left out of the
     * request are unchanged; a blank note clears it.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryTermNotFoundException if a new category or unit term is not visible to this organization
     * @throws InventoryItemAlreadyExistsException if the new name collides with another item
     */
    InventoryItemResponse updateItem(Long organizationId, Long itemId, UpdateInventoryItemRequest request, User currentUser);

    /**
     * Deletes an item and its variants.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryItemInUseException if any variant holds stock, or owes it below zero, anywhere
     * @throws InventoryItemLinkedToGoodieException if an event goody is handed out from this item
     * <p>The item's spellings go with it, since a spelling says nothing once its item is gone.
     */
    void deleteItem(Long organizationId, Long itemId, User currentUser);

    /**
     * Adds a variant to an existing item.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryVariantAlreadyExistsException if a variant with these attribute values already exists on the item
     */
    InventoryItemResponse addVariant(Long organizationId, Long itemId, CreateInventoryVariantRequest request, User currentUser);

    /**
     * Removes a variant. An item must always keep at least one.
     *
     * @throws InventoryVariantNotFoundException if the variant does not belong to this item
     * @throws InventoryVariantInUseException if the variant holds stock, or owes it below zero, anywhere
     * @throws InventoryVariantAliasedException if the item still reads a roster spelling as this variant
     */
    InventoryItemResponse removeVariant(Long organizationId, Long itemId, Long variantId, User currentUser);

    /**
     * Shows one photo on several variants of the item, so they share one stored file. The key must come
     * from the item's upload URL or already be used by this item or one of its variants. Each variant's
     * previous photo is unlinked, and its file deleted once nothing in the item uses it.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryVariantNotFoundException if a listed variant does not belong to this item
     * @throws com.timekeeper.bibexpo.storage.exception.InvalidFileException if the key does not belong to this item or was not uploaded
     */
    InventoryItemResponse applyVariantImage(Long organizationId, Long itemId, String objectKey, List<Long> variantIds,
                                            User currentUser);

    /**
     * Unlinks a variant's photo, if any. Its file is deleted once nothing in the item uses it.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryVariantNotFoundException if the variant does not belong to this item
     */
    InventoryItemResponse removeVariantImage(Long organizationId, Long itemId, Long variantId, User currentUser);

    /**
     * Generates a presigned upload URL for a photo of this item, for its primary photo, its variants, or both.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     */
    PresignUploadResponse createItemImageUploadUrl(Long organizationId, Long itemId, String contentType, User currentUser);

    /**
     * Makes a photo the item's primary photo. The key must come from the item's upload URL or already be
     * used by one of its variants. The previous primary photo is unlinked, and its file deleted once no
     * variant uses it.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws com.timekeeper.bibexpo.storage.exception.InvalidFileException if the key does not belong to this item or was not uploaded
     */
    InventoryItemResponse attachItemImage(Long organizationId, Long itemId, String objectKey, User currentUser);

    /**
     * Unlinks the item's primary photo, if any; its file is deleted once no variant uses it. Variant photos stay.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     */
    InventoryItemResponse removeItemImage(Long organizationId, Long itemId, User currentUser);
}
