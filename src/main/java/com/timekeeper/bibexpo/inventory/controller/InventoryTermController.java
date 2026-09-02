package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.exception.InventoryTermLimitReachedException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryTermResponse;
import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
import com.timekeeper.bibexpo.inventory.service.InventoryTermService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@RestController
@RequestMapping("/api/organizations/{organizationId}/inventory/terms")
@RequiredArgsConstructor
@Slf4j
public class InventoryTermController implements InventoryTermControllerApi {

    private final InventoryTermService termService;

    @Override
    public ResponseEntity<List<InventoryTermResponse>> listTerms(
            @PathVariable Long organizationId,
            @RequestParam TermKind kind,
            @AuthenticationPrincipal User currentUser) {
        log.info("Listing inventory terms of kind {} for organization {} by user {}",
                kind, organizationId, currentUser.getUsername());
        return ResponseEntity.ok(termService.listVisible(organizationId, kind, currentUser));
    }

    @Override
    public ResponseEntity<InventoryTermResponse> getTerm(
            @PathVariable Long organizationId,
            @PathVariable Long termId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(termService.getTerm(organizationId, termId, currentUser));
    }

    @Override
    public ResponseEntity<InventoryTermResponse> createTerm(
            @PathVariable Long organizationId,
            @RequestParam TermKind kind,
            @Valid @RequestBody CreateInventoryTermRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Creating inventory term of kind {} for organization {} by user {}",
                kind, organizationId, currentUser.getUsername());
        InventoryTermResponse response = termService.createTerm(organizationId, kind, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryTermResponse> updateTerm(
            @PathVariable Long organizationId,
            @PathVariable Long termId,
            @Valid @RequestBody UpdateInventoryTermRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Renaming inventory term {} for organization {} by user {}",
                termId, organizationId, currentUser.getUsername());
        return ResponseEntity.ok(termService.updateTerm(organizationId, termId, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteTerm(
            @PathVariable Long organizationId,
            @PathVariable Long termId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Deleting inventory term {} for organization {} by user {}",
                termId, organizationId, currentUser.getUsername());
        termService.deleteTerm(organizationId, termId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InventoryTermLimitReachedException.class)
    public ResponseEntity<ErrorResponse> handleTermLimitReached(
            InventoryTermLimitReachedException ex, WebRequest request) {
        log.info("Inventory term limit reached: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }
}
