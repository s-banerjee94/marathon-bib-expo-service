package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.exception.InventoryVariantLimitReachedException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryItemRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryVariantRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryItemRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryItemSummaryResponse;
import com.timekeeper.bibexpo.inventory.service.InventoryItemService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.shared.web.PageableResponse;
import com.timekeeper.bibexpo.storage.model.dto.request.AttachUploadRequest;
import com.timekeeper.bibexpo.storage.model.dto.request.PresignUploadRequest;
import com.timekeeper.bibexpo.storage.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;

@RestController
@RequestMapping("/api/organizations/{organizationId}/inventory/items")
@RequiredArgsConstructor
@Slf4j
public class InventoryItemController implements InventoryItemControllerApi {

    private final InventoryItemService itemService;

    @Override
    public ResponseEntity<PageableResponse<InventoryItemSummaryResponse>> listItems(
            @PathVariable Long organizationId,
            String name,
            Long categoryId,
            Instant createdFrom,
            Instant createdTo,
            Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(PageableResponse.of(itemService.listItems(
                organizationId, name, categoryId, createdFrom, createdTo, pageable, currentUser)));
    }

    @Override
    public ResponseEntity<InventoryItemResponse> getItem(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(itemService.getItem(organizationId, itemId, currentUser));
    }

    @Override
    public ResponseEntity<InventoryItemResponse> createItem(
            @PathVariable Long organizationId,
            @Valid @RequestBody CreateInventoryItemRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Creating inventory item '{}' for organization {} by user {}",
                request.getName(), organizationId, currentUser.getUsername());
        InventoryItemResponse response = itemService.createItem(organizationId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryItemResponse> updateItem(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateInventoryItemRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Updating inventory item {} for organization {} by user {}",
                itemId, organizationId, currentUser.getUsername());
        return ResponseEntity.ok(itemService.updateItem(organizationId, itemId, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteItem(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Deleting inventory item {} for organization {} by user {}",
                itemId, organizationId, currentUser.getUsername());
        itemService.deleteItem(organizationId, itemId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InventoryItemResponse> addVariant(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody CreateInventoryVariantRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Adding variant to inventory item {} for organization {} by user {}",
                itemId, organizationId, currentUser.getUsername());
        InventoryItemResponse response = itemService.addVariant(organizationId, itemId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryItemResponse> removeVariant(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long variantId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Removing variant {} from inventory item {} for organization {} by user {}",
                variantId, itemId, organizationId, currentUser.getUsername());
        return ResponseEntity.ok(itemService.removeVariant(organizationId, itemId, variantId, currentUser));
    }

    @Override
    public ResponseEntity<PresignUploadResponse> createVariantImageUploadUrl(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long variantId,
            @Valid @RequestBody PresignUploadRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Request variant image upload URL for variant {} on inventory item {} for organization {} by user {}",
                variantId, itemId, organizationId, currentUser.getUsername());
        PresignUploadResponse response = itemService.createVariantImageUploadUrl(
                organizationId, itemId, variantId, request.getContentType(), currentUser);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<InventoryItemResponse> attachVariantImage(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long variantId,
            @Valid @RequestBody AttachUploadRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Attaching image to variant {} on inventory item {} for organization {} by user {}",
                variantId, itemId, organizationId, currentUser.getUsername());
        InventoryItemResponse response = itemService.attachVariantImage(
                organizationId, itemId, variantId, request.getObjectKey(), currentUser);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<InventoryItemResponse> removeVariantImage(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long variantId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Removing image from variant {} on inventory item {} for organization {} by user {}",
                variantId, itemId, organizationId, currentUser.getUsername());
        return ResponseEntity.ok(itemService.removeVariantImage(organizationId, itemId, variantId, currentUser));
    }

    @ExceptionHandler(InventoryVariantLimitReachedException.class)
    public ResponseEntity<ErrorResponse> handleVariantLimitReached(
            InventoryVariantLimitReachedException ex, WebRequest request) {
        log.info("Inventory variant limit reached: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }
}
