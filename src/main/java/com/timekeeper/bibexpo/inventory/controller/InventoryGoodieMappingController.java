package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryGoodieMappingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryGoodieMappingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieMappingResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieResolutionResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryGoodieShortfallResponse;
import com.timekeeper.bibexpo.inventory.service.InventoryGoodieMappingService;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organizations/{organizationId}/inventory/events/{eventId}/goodies")
@RequiredArgsConstructor
public class InventoryGoodieMappingController implements InventoryGoodieMappingControllerApi {

    private final InventoryGoodieMappingService goodieMappingService;

    @Override
    public ResponseEntity<List<InventoryGoodieMappingResponse>> listMappings(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(goodieMappingService.listMappings(organizationId, eventId, currentUser));
    }

    @Override
    public ResponseEntity<List<InventoryGoodieResolutionResponse>> resolveGoodies(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(goodieMappingService.resolveGoodies(organizationId, eventId, currentUser));
    }

    @Override
    public ResponseEntity<List<InventoryGoodieShortfallResponse>> shortfall(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(goodieMappingService.shortfall(organizationId, eventId, currentUser));
    }

    @Override
    public ResponseEntity<InventoryGoodieMappingResponse> createMapping(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @Valid @RequestBody CreateInventoryGoodieMappingRequest request,
            @AuthenticationPrincipal User currentUser) {
        InventoryGoodieMappingResponse response =
                goodieMappingService.createMapping(organizationId, eventId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryGoodieMappingResponse> updateMapping(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @PathVariable Long mappingId,
            @Valid @RequestBody UpdateInventoryGoodieMappingRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                goodieMappingService.updateMapping(organizationId, eventId, mappingId, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteMapping(
            @PathVariable Long organizationId,
            @PathVariable Long eventId,
            @PathVariable Long mappingId,
            @AuthenticationPrincipal User currentUser) {
        goodieMappingService.deleteMapping(organizationId, eventId, mappingId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
