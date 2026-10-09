package com.timekeeper.bibexpo.distribution.controller;

import com.timekeeper.bibexpo.distribution.exception.BibAlreadyCollectedException;
import com.timekeeper.bibexpo.distribution.exception.BibNotCollectedException;
import com.timekeeper.bibexpo.distribution.exception.GoodiesAlreadyDistributedException;
import com.timekeeper.bibexpo.distribution.exception.GoodiesItemNotFoundException;
import com.timekeeper.bibexpo.distribution.model.dto.request.BulkCollectBibRequest;
import com.timekeeper.bibexpo.distribution.model.dto.request.BulkDistributeGoodiesRequest;
import com.timekeeper.bibexpo.distribution.model.dto.request.CollectBibRequest;
import com.timekeeper.bibexpo.distribution.model.dto.request.DistributeGoodiesRequest;
import com.timekeeper.bibexpo.distribution.model.dto.response.BibDistributionResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.BulkDistributionResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.DistributionGoodieResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.DistributionLogListResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.DistributionLogResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.GoodiesDistributionResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.PendingParticipantListResponse;
import com.timekeeper.bibexpo.distribution.model.dto.response.UndoDistributionResponse;
import com.timekeeper.bibexpo.distribution.model.enums.LogSearchType;
import com.timekeeper.bibexpo.distribution.model.enums.PendingType;
import com.timekeeper.bibexpo.distribution.service.DistributionService;
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
@RequestMapping("/api/events/{eventId}/distribution")
@RequiredArgsConstructor
@Slf4j
public class DistributionController implements DistributionControllerApi {

    private final DistributionService distributionService;

    @Override
    public ResponseEntity<BibDistributionResponse> collectBib(
            @PathVariable Long eventId,
            @PathVariable String bibNumber,
            @Valid @RequestBody(required = false) CollectBibRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(distributionService.collectBib(eventId, bibNumber, request, currentUser));
    }

    @Override
    public ResponseEntity<UndoDistributionResponse> undoBib(
            @PathVariable Long eventId,
            @PathVariable String bibNumber,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(distributionService.undoBib(eventId, bibNumber, currentUser));
    }

    @Override
    public ResponseEntity<GoodiesDistributionResponse> distributeGoodies(
            @PathVariable Long eventId,
            @PathVariable String bibNumber,
            @Valid @RequestBody DistributeGoodiesRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(distributionService.distributeGoodies(eventId, bibNumber, request, currentUser));
    }

    @Override
    public ResponseEntity<List<DistributionGoodieResponse>> listGoodies(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(distributionService.listGoodies(eventId, currentUser));
    }

    @Override
    public ResponseEntity<PendingParticipantListResponse> getPending(
            @PathVariable Long eventId,
            @RequestParam PendingType type,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String lastEvaluatedKey,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(distributionService.getPending(eventId, type, limit, lastEvaluatedKey, currentUser));
    }

    @Override
    public ResponseEntity<DistributionLogListResponse> getDistributionLogs(
            @PathVariable Long eventId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String lastEvaluatedKey,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(distributionService.getDistributionLogs(eventId, limit, lastEvaluatedKey, currentUser));
    }

    @Override
    public ResponseEntity<List<DistributionLogResponse>> getParticipantLogs(
            @PathVariable Long eventId,
            @PathVariable String bibNumber,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(distributionService.getParticipantLogs(eventId, bibNumber, currentUser));
    }

    @Override
    public ResponseEntity<DistributionLogListResponse> lookupLogs(
            @PathVariable Long eventId,
            @RequestParam LogSearchType searchType,
            @RequestParam String searchValue,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(required = false) String lastEvaluatedKey,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(distributionService.lookupLogs(
                eventId, searchType, searchValue, limit, lastEvaluatedKey, currentUser));
    }

    @Override
    public ResponseEntity<BulkDistributionResponse> bulkCollectBib(
            @PathVariable Long eventId,
            @Valid @RequestBody BulkCollectBibRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(distributionService.bulkCollectBib(eventId, request, currentUser));
    }

    @Override
    public ResponseEntity<BulkDistributionResponse> bulkDistributeGoodies(
            @PathVariable Long eventId,
            @Valid @RequestBody BulkDistributeGoodiesRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(distributionService.bulkDistributeGoodies(eventId, request, currentUser));
    }

    // Distribution-only exceptions are handled here rather than in the global advice, since they can
    // only be thrown from this controller's flows. Cross-cutting ones stay in GlobalExceptionHandler.

    @ExceptionHandler(BibAlreadyCollectedException.class)
    public ResponseEntity<ErrorResponse> handleBibAlreadyCollected(BibAlreadyCollectedException ex, WebRequest request) {
        logRejected(HttpStatus.CONFLICT, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    @ExceptionHandler(BibNotCollectedException.class)
    public ResponseEntity<ErrorResponse> handleBibNotCollected(BibNotCollectedException ex, WebRequest request) {
        logRejected(HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }

    @ExceptionHandler(GoodiesItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGoodiesItemNotFound(GoodiesItemNotFoundException ex, WebRequest request) {
        logRejected(HttpStatus.NOT_FOUND, ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }

    @ExceptionHandler(GoodiesAlreadyDistributedException.class)
    public ResponseEntity<ErrorResponse> handleGoodiesAlreadyDistributed(GoodiesAlreadyDistributedException ex, WebRequest request) {
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
