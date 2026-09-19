package com.timekeeper.bibexpo.inventory.service;

import com.timekeeper.bibexpo.inventory.exception.InventoryItemAlreadyExistsException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemInUseException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Manages items (the things an organization stocks) and their variants (size, colour, flavour).
 * An item always carries at least one variant — {@code DEFAULT} when none is given — so every
 * stock-writing path always has a variant to work with.
 *
 * <p>Organization-scoped only.
 */
public interface InventoryItemService {

    /**
     * One page of the organization's items, without their attributes or variants — those grow with
     * the catalogue and belong to {@link #getItem}, which fetches them for a single item.
     *
     * <p>Every filter is optional and they combine: a name fragment matched anywhere in the name
     * and ignoring case, one category, and a closed range over the day the item was added. Each
     * one left out narrows nothing.
     *
     * <p>Newest first unless the caller asks for another order, so paging through the list twice
     * sees the same rows in the same places.
     *
     * @throws com.timekeeper.bibexpo.shared.error.InvalidUserDataException if the range starts after it ends
     */
    Page<InventoryItemSummaryResponse> listItems(Long organizationId, String name, Long categoryId,
                                                 Instant createdFrom, Instant createdTo,
                                                 Pageable pageable, User currentUser);

    /** One item with its attributes, its variants and their attribute values. */
    InventoryItemResponse getItem(Long organizationId, Long itemId, User currentUser);

    /**
     * Adds a new item.
     *
     * @throws InventoryTermNotFoundException if the category or unit term is not visible to this organization
     * @throws InventoryItemAlreadyExistsException if an item with this name already exists
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
     * Generates a presigned upload URL for a variant's image.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryVariantNotFoundException if the variant does not belong to this item
     */
    PresignUploadResponse createVariantImageUploadUrl(Long organizationId, Long itemId, Long variantId,
                                                        String contentType, User currentUser);

    /**
     * Attaches a previously uploaded image to a variant, replacing any existing one.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryVariantNotFoundException if the variant does not belong to this item
     * @throws com.timekeeper.bibexpo.storage.exception.InvalidFileException if the object does not belong to this variant or was not uploaded
     */
    InventoryItemResponse attachVariantImage(Long organizationId, Long itemId, Long variantId, String objectKey, User currentUser);

    /**
     * Removes a variant's image, if any.
     *
     * @throws InventoryItemNotFoundException if the item does not belong to this organization
     * @throws InventoryVariantNotFoundException if the variant does not belong to this item
     */
    InventoryItemResponse removeVariantImage(Long organizationId, Long itemId, Long variantId, User currentUser);
}
