package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.model.dto.request.CreateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.UpdateInventoryTermRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.InventoryTermResponse;
import com.timekeeper.bibexpo.inventory.model.enums.TermKind;
import com.timekeeper.bibexpo.inventory.service.InventoryTermService;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SystemInventoryTermController implements SystemInventoryTermControllerApi {

    private final InventoryTermService termService;

    @Override
    public ResponseEntity<List<InventoryTermResponse>> listTerms(
            @RequestParam TermKind kind,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(termService.listVisible(null, kind, currentUser));
    }

    @Override
    public ResponseEntity<InventoryTermResponse> getTerm(
            @PathVariable Long termId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(termService.getTerm(null, termId, currentUser));
    }

    @Override
    public ResponseEntity<InventoryTermResponse> createTerm(
            @RequestParam TermKind kind,
            @Valid @RequestBody CreateInventoryTermRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Root creating platform default inventory term of kind {} by user {}",
                kind, currentUser.getUsername());
        InventoryTermResponse response = termService.createTerm(null, kind, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InventoryTermResponse> updateTerm(
            @PathVariable Long termId,
            @Valid @RequestBody UpdateInventoryTermRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Root renaming platform default inventory term {} by user {}", termId, currentUser.getUsername());
        return ResponseEntity.ok(termService.updateTerm(null, termId, request, currentUser));
    }

    @Override
    public ResponseEntity<Void> deleteTerm(
            @PathVariable Long termId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Root deleting platform default inventory term {} by user {}", termId, currentUser.getUsername());
        termService.deleteTerm(null, termId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
