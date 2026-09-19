package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.exception.InventoryAttributeOptionLimitReachedException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryAttributeRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryAttributeOptionRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryAttributeRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryAttributeOptionRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryAttributeListResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryAttributeResponse;
import com.timekeeper.bibexpo.inventory.service.InventoryAttributeService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
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

@RestController
@RequestMapping("/api/organizations/{organizationId}/inventory/attributes")
@RequiredArgsConstructor
@Slf4j
public class InventoryAttributeController implements InventoryAttributeControllerApi {

    private final InventoryAttributeService attributeService;

    @Override
    public ResponseEntity<InventoryAttributeListResponse> listAttributes(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(attributeService.listVisible(organizationId, currentUser));
    }

    @Override
    public ResponseEntity<InventoryAttributeResponse> getAttribute(
            @PathVariable Long organizationId,
            @PathVariable Long attributeId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(attributeService.getAttribute(organizationId, attributeId, currentUser));
    }

    @Override
    public ResponseEntity<InventoryAttributeResponse> createAttribute(
            @PathVariable Long organizationId,
            @Valid @RequestBody CreateInventoryAttributeRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Creating inventory attribute '{}' for organization {} by user {}",
                request.getName(), organizationId, currentUser.getUsername());
        InventoryAttributeResponse response =
                attributeService.createAttribute(organizationId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryAttributeResponse> updateAttribute(
            @PathVariable Long organizationId,
            @PathVariable Long attributeId,
            @Valid @RequestBody UpdateInventoryAttributeRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Updating inventory attribute {} for organization {} by user {}",
                attributeId, organizationId, currentUser.getUsername());
        return ResponseEntity.ok(attributeService.updateAttribute(organizationId, attributeId, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteAttribute(
            @PathVariable Long organizationId,
            @PathVariable Long attributeId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Deleting inventory attribute {} for organization {} by user {}",
                attributeId, organizationId, currentUser.getUsername());
        attributeService.deleteAttribute(organizationId, attributeId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InventoryAttributeResponse> addOption(
            @PathVariable Long organizationId,
            @PathVariable Long attributeId,
            @Valid @RequestBody CreateInventoryAttributeOptionRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Adding value '{}' to inventory attribute {} for organization {} by user {}",
                request.getValue(), attributeId, organizationId, currentUser.getUsername());
        InventoryAttributeResponse response =
                attributeService.addOption(organizationId, attributeId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryAttributeResponse> updateOption(
            @PathVariable Long organizationId,
            @PathVariable Long attributeId,
            @PathVariable Long optionId,
            @Valid @RequestBody UpdateInventoryAttributeOptionRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Renaming value {} on inventory attribute {} for organization {} by user {}",
                optionId, attributeId, organizationId, currentUser.getUsername());
        return ResponseEntity.ok(attributeService.updateOption(organizationId, attributeId, optionId, request, currentUser));
    }

    @Override
    public ResponseEntity<InventoryAttributeResponse> removeOption(
            @PathVariable Long organizationId,
            @PathVariable Long attributeId,
            @PathVariable Long optionId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Removing value {} from inventory attribute {} for organization {} by user {}",
                optionId, attributeId, organizationId, currentUser.getUsername());
        return ResponseEntity.ok(attributeService.removeOption(organizationId, attributeId, optionId, currentUser));
    }

    @ExceptionHandler(InventoryAttributeOptionLimitReachedException.class)
    public ResponseEntity<ErrorResponse> handleOptionLimitReached(
            InventoryAttributeOptionLimitReachedException ex, WebRequest request) {
        log.info("Inventory attribute value limit reached: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }
}
