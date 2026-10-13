package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.exception.StockPostingConflictException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateStockPostingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.ReverseStockPostingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.ItemActivityResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.ItemStockResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockMovementResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockPostingConflictResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockPostingResponse;
import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
import com.timekeeper.bibexpo.inventory.service.StockService;
import com.timekeeper.bibexpo.shared.web.PageableResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;

@RestController
@RequestMapping("/api/organizations/{organizationId}/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryStockController implements InventoryStockControllerApi {

    private final StockService stockService;

    @Override
    public ResponseEntity<PageableResponse<StockItemResponse>> listStock(
            @PathVariable Long organizationId,
            String search,
            Long categoryId,
            Long locationId,
            Boolean lowOnly,
            Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(PageableResponse.of(stockService.listStock(organizationId, search,
                categoryId, locationId, lowOnly, pageable, currentUser)));
    }

    @Override
    public ResponseEntity<PageableResponse<StockMovementResponse>> listMovements(
            @PathVariable Long organizationId,
            Long itemId,
            Long variantId,
            MovementType type,
            MovementReason reason,
            String performedBy,
            Instant occurredFrom,
            Instant occurredTo,
            Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(PageableResponse.of(stockService.listMovements(organizationId, itemId,
                variantId, type, reason, performedBy, occurredFrom, occurredTo, pageable, currentUser)));
    }

    @Override
    public ResponseEntity<ItemStockResponse> getItemStock(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(stockService.getItemStock(organizationId, itemId, currentUser));
    }

    @Override
    public ResponseEntity<PageableResponse<ItemActivityResponse>> listItemActivity(
            @PathVariable Long organizationId,
            @PathVariable Long itemId,
            Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(PageableResponse.of(stockService.listItemActivity(organizationId, itemId, pageable,
                currentUser)));
    }

    // A double tap sends one key twice at once: the second request waits on the first's key, fails on it once
    // the first commits, and the retry, in a fresh transaction, finds that posting and answers with it.
    @Override
    public ResponseEntity<StockPostingResponse> postStock(
            @PathVariable Long organizationId,
            String idempotencyKey,
            @Valid @RequestBody CreateStockPostingRequest request,
            @AuthenticationPrincipal User currentUser) {
        try {
            return ResponseEntity.ok(stockService.postStock(organizationId, idempotencyKey, request, currentUser));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.ok(stockService.postStock(organizationId, idempotencyKey, request, currentUser));
        }
    }

    @Override
    public ResponseEntity<StockPostingResponse> reversePosting(
            @PathVariable Long organizationId,
            @PathVariable Long postingId,
            String idempotencyKey,
            @Valid @RequestBody(required = false) ReverseStockPostingRequest request,
            @AuthenticationPrincipal User currentUser) {
        try {
            return ResponseEntity.ok(stockService.reversePosting(organizationId, postingId, idempotencyKey, request,
                    currentUser));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.ok(stockService.reversePosting(organizationId, postingId, idempotencyKey, request,
                    currentUser));
        }
    }

    @ExceptionHandler(StockPostingConflictException.class)
    public ResponseEntity<StockPostingConflictResponse> handleStockPostingConflict(
            StockPostingConflictException ex, WebRequest request) {
        log.atWarn()
                .addKeyValue("event", "request.rejected")
                .addKeyValue("status", HttpStatus.CONFLICT.value())
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .addKeyValue("reason", ex.getMessage())
                .addKeyValue("lineCount", ex.getLines().size())
                .log("Request rejected");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(StockPostingConflictResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .lines(ex.getLines())
                .build());
    }
}
