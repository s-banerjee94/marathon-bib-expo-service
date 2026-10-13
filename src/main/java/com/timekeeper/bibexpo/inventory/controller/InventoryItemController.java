package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.exception.InventoryItemLimitReachedException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantLimitReachedException;
import com.timekeeper.bibexpo.inventory.model.dto.request.ApplyVariantImageRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryItemRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryVariantRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryItemRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryItemSummaryResponse;
import com.timekeeper.bibexpo.inventory.service.InventoryItemService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.storage.model.dto.request.AttachUploadRequest;
import com.timekeeper.bibexpo.storage.model.dto.request.PresignUploadRequest;
import com.timekeeper.bibexpo.storage.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@RestController
@RequestMapping("/api/organizations/{organizationId}/inventory/items")
@RequiredArgsConstructor
@Slf4j
public class InventoryItemController implements InventoryItemControllerApi {

    private final InventoryItemService itemService;

    @Override
    public ResponseEntity<List<InventoryItemSummaryResponse>> listItems(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(itemService.listItems(organizationId, currentUser));
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
        InventoryItemResponse response = itemService.createItem(organizationId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryItemResponse> updateItem(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateInventoryItemRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(itemService.updateItem(organizationId, itemId, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteItem(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal User currentUser) {
        itemService.deleteItem(organizationId, itemId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InventoryItemResponse> addVariant(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody CreateInventoryVariantRequest request,
            @AuthenticationPrincipal User currentUser) {
        InventoryItemResponse response = itemService.addVariant(organizationId, itemId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryItemResponse> removeVariant(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long variantId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(itemService.removeVariant(organizationId, itemId, variantId, currentUser));
    }

    @Override
    public ResponseEntity<InventoryItemResponse> applyVariantImage(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody ApplyVariantImageRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(itemService.applyVariantImage(organizationId, itemId, request.getObjectKey(),
                request.getVariantIds(), currentUser));
    }

    @Override
    public ResponseEntity<InventoryItemResponse> removeVariantImage(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long variantId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(itemService.removeVariantImage(organizationId, itemId, variantId, currentUser));
    }

    @Override
    public ResponseEntity<PresignUploadResponse> createItemImageUploadUrl(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody PresignUploadRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(itemService.createItemImageUploadUrl(organizationId, itemId,
                request.getContentType(), currentUser));
    }

    @Override
    public ResponseEntity<InventoryItemResponse> attachItemImage(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody AttachUploadRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(itemService.attachItemImage(organizationId, itemId, request.getObjectKey(), currentUser));
    }

    @Override
    public ResponseEntity<InventoryItemResponse> removeItemImage(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(itemService.removeItemImage(organizationId, itemId, currentUser));
    }

    @ExceptionHandler({InventoryItemLimitReachedException.class, InventoryVariantLimitReachedException.class})
    public ResponseEntity<ErrorResponse> handleLimitReached(RuntimeException ex, WebRequest request) {
        log.atWarn()
                .addKeyValue("event", "request.rejected")
                .addKeyValue("status", HttpStatus.CONFLICT.value())
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .addKeyValue("reason", ex.getMessage())
                .log("Request rejected");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }
}
