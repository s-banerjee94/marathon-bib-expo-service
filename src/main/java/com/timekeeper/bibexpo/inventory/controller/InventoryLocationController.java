package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.exception.InventoryLocationLimitReachedException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryLocationRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryLocationRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryLocationResponse;
import com.timekeeper.bibexpo.inventory.service.InventoryLocationService;
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
            String name,
            Long typeId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(locationService.listLocations(organizationId, name, typeId, currentUser));
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

    @Override
    public ResponseEntity<Void> deleteLocation(
            @PathVariable Long organizationId,
            @PathVariable Long locationId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Deleting inventory location {} for organization {} by user {}",
                locationId, organizationId, currentUser.getUsername());
        locationService.deleteLocation(organizationId, locationId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InventoryLocationLimitReachedException.class)
    public ResponseEntity<ErrorResponse> handleLocationLimitReached(
            InventoryLocationLimitReachedException ex, WebRequest request) {
        log.info("Inventory location limit reached: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }
}
