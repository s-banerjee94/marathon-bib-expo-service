package com.timekeeper.bibexpo.organization.controller;

import com.timekeeper.bibexpo.organization.exception.OrganizationAlreadyExistsException;
import com.timekeeper.bibexpo.organization.exception.OrganizationDeletionNotAllowedException;
import com.timekeeper.bibexpo.organization.exception.LimitReductionException;
import com.timekeeper.bibexpo.organization.model.dto.request.CreateOrganizationRequest;
import com.timekeeper.bibexpo.organization.model.dto.request.UpdateOrganizationRequest;
import com.timekeeper.bibexpo.organization.model.dto.request.UpdateOrganizationSubscriptionRequest;
import com.timekeeper.bibexpo.organization.model.dto.response.MembersUnlockedResponse;
import com.timekeeper.bibexpo.organization.model.dto.response.OrganizationResponse;
import com.timekeeper.bibexpo.organization.service.OrganizationService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.api.CurrentActor;
import com.timekeeper.bibexpo.shared.web.PageableResponse;
import com.timekeeper.bibexpo.storage.model.dto.request.AttachUploadRequest;
import com.timekeeper.bibexpo.storage.model.dto.request.PresignUploadRequest;
import com.timekeeper.bibexpo.storage.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@Slf4j
public class OrganizationController implements OrganizationControllerApi {

    private final OrganizationService organizationService;

    @Override
    public ResponseEntity<PageableResponse<OrganizationResponse>> getAllOrganizations(
            Boolean enabled, String search, Pageable pageable, User currentUser) {
        Page<OrganizationResponse> page = organizationService.getAllOrganizations(
                enabled, search, pageable, CurrentActor.from(currentUser));
        return ResponseEntity.ok(PageableResponse.of(page));
    }

    @Override
    public ResponseEntity<OrganizationResponse> createOrganization(CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationService.createOrganization(request));
    }

    @Override
    public ResponseEntity<OrganizationResponse> updateOrganization(
            Long id, UpdateOrganizationRequest request, User currentUser) {
        return ResponseEntity.ok(organizationService.updateOrganization(id, request, CurrentActor.from(currentUser)));
    }

    @Override
    public ResponseEntity<OrganizationResponse> updateSubscription(
            Long id, UpdateOrganizationSubscriptionRequest request) {
        return ResponseEntity.ok(organizationService.updateSubscription(id, request));
    }

    @Override
    public ResponseEntity<MembersUnlockedResponse> unlockMembers(Long id) {
        return ResponseEntity.ok(organizationService.unlockMembers(id));
    }

    @Override
    public ResponseEntity<OrganizationResponse> toggleOrganizationStatus(Long id, Boolean enabled) {
        return ResponseEntity.ok(organizationService.toggleOrganizationStatus(id, enabled));
    }

    @Override
    public ResponseEntity<Void> deleteOrganization(Long id, User currentUser) {
        organizationService.deleteOrganization(id, CurrentActor.from(currentUser));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<OrganizationResponse> getOrganizationById(Long id, User currentUser) {
        return ResponseEntity.ok(organizationService.getOrganizationById(id, CurrentActor.from(currentUser)));
    }

    @Override
    public ResponseEntity<OrganizationResponse> getCurrentUserOrganization(User currentUser) {
        return ResponseEntity.ok(organizationService.getCurrentUserOrganization(CurrentActor.from(currentUser)));
    }

    @Override
    public ResponseEntity<PresignUploadResponse> createLogoUploadUrl(
            Long id, PresignUploadRequest request, User currentUser) {
        PresignUploadResponse response = organizationService.createLogoUploadUrl(
                id, request.getContentType(), CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<OrganizationResponse> attachLogo(
            Long id, AttachUploadRequest request, User currentUser) {
        OrganizationResponse response = organizationService.attachLogo(id, request.getObjectKey(), CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<OrganizationResponse> removeLogo(Long id, User currentUser) {
        OrganizationResponse response = organizationService.removeLogo(id, CurrentActor.from(currentUser));
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(OrganizationDeletionNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationDeletionNotAllowed(
            OrganizationDeletionNotAllowedException ex, WebRequest request) {
        logRejected(HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }

    @ExceptionHandler(OrganizationAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationAlreadyExists(
            OrganizationAlreadyExistsException ex, WebRequest request) {
        logRejected(HttpStatus.CONFLICT, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    @ExceptionHandler(LimitReductionException.class)
    public ResponseEntity<ErrorResponse> handleLimitReduction(
            LimitReductionException ex, WebRequest request) {
        logRejected(HttpStatus.CONFLICT, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    private void logRejected(HttpStatus status, Exception ex) {
        log.atWarn()
                .addKeyValue("event", "request.rejected")
                .addKeyValue("status", status.value())
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .addKeyValue("reason", ex.getMessage())
                .log("Request rejected");
    }
}
