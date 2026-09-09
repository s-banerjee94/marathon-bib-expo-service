package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryVariantAliasRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryVariantAliasRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryVariantAliasResponse;
import com.timekeeper.bibexpo.inventory.service.InventoryVariantAliasService;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organizations/{organizationId}/inventory/items/{itemId}/variant-aliases")
@RequiredArgsConstructor
@Slf4j
public class InventoryVariantAliasController implements InventoryVariantAliasControllerApi {

    private final InventoryVariantAliasService aliasService;

    @Override
    public ResponseEntity<List<InventoryVariantAliasResponse>> listAliases(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(aliasService.listAliases(organizationId, itemId, currentUser));
    }

    @Override
    public ResponseEntity<InventoryVariantAliasResponse> createAlias(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @Valid @RequestBody CreateInventoryVariantAliasRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Teaching item {} to read '{}' as variant {} by user {}",
                itemId, request.getSourceValue(), request.getVariantId(), currentUser.getUsername());
        InventoryVariantAliasResponse response =
                aliasService.createAlias(organizationId, itemId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryVariantAliasResponse> updateAlias(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long aliasId,
            @Valid @RequestBody UpdateInventoryVariantAliasRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Pointing spelling {} of item {} at variant {} by user {}",
                aliasId, itemId, request.getVariantId(), currentUser.getUsername());
        return ResponseEntity.ok(
                aliasService.updateAlias(organizationId, itemId, aliasId, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteAlias(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @PathVariable Long aliasId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Removing spelling {} of item {} by user {}",
                aliasId, itemId, currentUser.getUsername());
        aliasService.deleteAlias(organizationId, itemId, aliasId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
