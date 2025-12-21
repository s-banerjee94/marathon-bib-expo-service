package com.timekeeper.bibexpo.controller;


import com.timekeeper.bibexpo.model.dto.request.CreateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateOrganizationRequest;
import com.timekeeper.bibexpo.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@Slf4j
public class OrganizationController implements OrganizationControllerApi {

    private final OrganizationService organizationService;

    @Override
    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {
        log.info("Received request to create organization: {}", request.getOrganizerName());

        OrganizationResponse response = organizationService.createOrganization(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @PatchMapping("/{id}")
    public ResponseEntity<OrganizationResponse> updateOrganization(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrganizationRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to update organization ID: {} by user: {}",
                id, currentUser.getUsername());

        OrganizationResponse response = organizationService.updateOrganization(id, request, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrganizationResponse> toggleOrganizationStatus(
            @PathVariable Long id,
            @RequestBody Boolean enabled) {
        log.info("Received request to {} organization ID: {}",
                Boolean.TRUE.equals(enabled) ? "enable" : "disable", id);

        OrganizationResponse response = organizationService.toggleOrganizationStatus(id, enabled);

        return ResponseEntity.ok(response);
    }
}
