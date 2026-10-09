package com.timekeeper.bibexpo.inventory.controller;

import com.timekeeper.bibexpo.inventory.exception.StockCapacityExceededException;
import com.timekeeper.bibexpo.inventory.model.dto.request.AdjustStockRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.ReceiveStockRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.TransferStockRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockBalanceResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockMovementResponse;
import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
import com.timekeeper.bibexpo.inventory.service.StockService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.shared.web.PageableResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;

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
            Long locationId,
            Boolean lowOnly,
            Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(PageableResponse.of(stockService.listStock(organizationId, search,
                locationId, lowOnly, pageable, currentUser)));
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
    public ResponseEntity<StockBalanceResponse> receiveStock(
            @PathVariable Long organizationId,
            @Valid @RequestBody ReceiveStockRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(stockService.receiveStock(organizationId, request, currentUser));
    }

    @Override
    public ResponseEntity<List<StockBalanceResponse>> transferStock(
            @PathVariable Long organizationId,
            @Valid @RequestBody TransferStockRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(stockService.transferStock(organizationId, request, currentUser));
    }

    @Override
    public ResponseEntity<StockBalanceResponse> adjustStock(
            @PathVariable Long organizationId,
            @Valid @RequestBody AdjustStockRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(stockService.adjustStock(organizationId, request, currentUser));
    }

    @ExceptionHandler(StockCapacityExceededException.class)
    public ResponseEntity<ErrorResponse> handleStockCapacityExceeded(
            StockCapacityExceededException ex, WebRequest request) {
        log.atWarn()
                .addKeyValue("event", "request.rejected")
                .addKeyValue("status", HttpStatus.CONFLICT.value())
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .addKeyValue("reason", ex.getMessage())
                .log("Request rejected");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }
}
