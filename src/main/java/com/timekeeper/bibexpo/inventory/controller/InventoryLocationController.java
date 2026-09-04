package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryLocationRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryLocationRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryLocationResponse;
import com.timekeeper.bibexpo.inventory.service.InventoryLocationService;
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
@RequestMapping("/api/organizations/{organizationId}/inventory/locations")
@RequiredArgsConstructor
@Slf4j
public class InventoryLocationController implements InventoryLocationControllerApi {

    private final InventoryLocationService locationService;

    @Override
    public ResponseEntity<List<InventoryLocationResponse>> listLocations(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(locationService.listLocations(organizationId, currentUser));
    }

    @Override
    public ResponseEntity<InventoryLocationResponse> createLocation(
            @PathVariable Long organizationId,
            @Valid @RequestBody CreateInventoryLocationRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Creating inventory location '{}' for organization {} by user {}",
                request.getName(), organizationId, currentUser.getUsername());
        InventoryLocationResponse response = locationService.createLocation(organizationId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryLocationResponse> updateLocation(
            @PathVariable Long organizationId,
            @PathVariable Long locationId,
            @Valid @RequestBody UpdateInventoryLocationRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Updating inventory location {} for organization {} by user {}",
                locationId, organizationId, currentUser.getUsername());
        return ResponseEntity.ok(locationService.updateLocation(organizationId, locationId, request, currentUser));
    }
}
