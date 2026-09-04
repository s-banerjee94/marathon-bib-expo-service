package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryAttributeRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryAttributeOptionRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryAttributeRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryAttributeOptionRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryAttributeResponse;
import com.timekeeper.bibexpo.inventory.service.InventoryAttributeService;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SystemInventoryAttributeController implements SystemInventoryAttributeControllerApi {

    private final InventoryAttributeService attributeService;

    @Override
    public ResponseEntity<List<InventoryAttributeResponse>> listAttributes(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(attributeService.listVisible(null, currentUser).getPlatformDefaults());
    }

    @Override
    public ResponseEntity<InventoryAttributeResponse> getAttribute(
            @PathVariable Long attributeId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(attributeService.getAttribute(null, attributeId, currentUser));
    }

    @Override
    public ResponseEntity<InventoryAttributeResponse> createAttribute(
            @Valid @RequestBody CreateInventoryAttributeRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Root creating platform default inventory attribute '{}' by user {}", request.getName(), currentUser.getUsername());
        InventoryAttributeResponse response =
                attributeService.createAttribute(null, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryAttributeResponse> updateAttribute(
            @PathVariable Long attributeId,
            @Valid @RequestBody UpdateInventoryAttributeRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Root updating platform default inventory attribute {} by user {}", attributeId, currentUser.getUsername());
        return ResponseEntity.ok(attributeService.updateAttribute(null, attributeId, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteAttribute(
            @PathVariable Long attributeId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Root deleting platform default inventory attribute {} by user {}", attributeId, currentUser.getUsername());
        attributeService.deleteAttribute(null, attributeId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InventoryAttributeResponse> addOption(
            @PathVariable Long attributeId,
            @Valid @RequestBody CreateInventoryAttributeOptionRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Root adding value '{}' to platform default inventory attribute {} by user {}",
                request.getValue(), attributeId, currentUser.getUsername());
        InventoryAttributeResponse response =
                attributeService.addOption(null, attributeId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryAttributeResponse> updateOption(
            @PathVariable Long attributeId,
            @PathVariable Long optionId,
            @Valid @RequestBody UpdateInventoryAttributeOptionRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Root renaming value {} on platform default inventory attribute {} by user {}",
                optionId, attributeId, currentUser.getUsername());
        return ResponseEntity.ok(attributeService.updateOption(null, attributeId, optionId, request, currentUser));
    }

    @Override
    public ResponseEntity<InventoryAttributeResponse> removeOption(
            @PathVariable Long attributeId,
            @PathVariable Long optionId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Root removing value {} from platform default inventory attribute {} by user {}",
                optionId, attributeId, currentUser.getUsername());
        return ResponseEntity.ok(attributeService.removeOption(null, attributeId, optionId, currentUser));
    }
}
