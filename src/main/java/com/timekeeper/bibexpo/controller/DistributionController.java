package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.request.BulkCollectBibRequest;
import com.timekeeper.bibexpo.model.dto.request.BulkDistributeGoodiesRequest;
import com.timekeeper.bibexpo.model.dto.request.CollectBibRequest;
import com.timekeeper.bibexpo.model.dto.request.DistributeGoodiesRequest;
import com.timekeeper.bibexpo.model.dto.response.BibDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.DistributionLogListResponse;
import com.timekeeper.bibexpo.model.enums.LogSearchType;
import com.timekeeper.bibexpo.model.dto.response.BulkDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.DistributionLogResponse;
import com.timekeeper.bibexpo.model.dto.response.GoodiesDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantDistributionResponse;
import com.timekeeper.bibexpo.model.dto.response.PendingBibListResponse;
import com.timekeeper.bibexpo.model.dto.response.PendingGoodiesListResponse;
import com.timekeeper.bibexpo.model.dto.response.UndoDistributionResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.DistributionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestBody(required = false) CollectBibRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to collect bib {} for event {} by staff: {}",
                bibNumber, eventId, currentUser.getUsername());

        BibDistributionResponse response = distributionService.collectBib(eventId, bibNumber, request, currentUser);

        log.info("Bib {} collected successfully for event {}", bibNumber, eventId);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UndoDistributionResponse> undoBib(
            @PathVariable Long eventId,
            @PathVariable String bibNumber,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to undo bib collection {} for event {} by user: {}",
                bibNumber, eventId, currentUser.getUsername());

        UndoDistributionResponse response = distributionService.undoBib(eventId, bibNumber, currentUser);

        log.info("Bib {} collection undone successfully for event {}", bibNumber, eventId);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<GoodiesDistributionResponse> distributeGoodies(
            @PathVariable Long eventId,
            @PathVariable String bibNumber,
            @RequestBody DistributeGoodiesRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to distribute goodies items {} for bib {} in event {} by staff: {}",
                request.getGoodiesItems(), bibNumber, eventId, currentUser.getUsername());

        GoodiesDistributionResponse response = distributionService.distributeGoodies(eventId, bibNumber, request, currentUser);

        log.info("Goodies items {} distributed successfully for bib {} in event {}",
                request.getGoodiesItems(), bibNumber, eventId);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PendingBibListResponse> getPendingBibs(
            @PathVariable Long eventId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String lastEvaluatedKey,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get pending bib collection list for event {} (limit: {}) by user: {}",
                eventId, limit, currentUser.getUsername());

        PendingBibListResponse response = distributionService.getPendingBibs(eventId, limit, lastEvaluatedKey, currentUser);

        log.info("Found {} participants with pending bib collection for event {}, hasMore: {}",
                response.getCount(), eventId, response.getHasMore());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<DistributionLogListResponse> getDistributionLogs(
            @PathVariable Long eventId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String lastEvaluatedKey,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get distribution logs for event {} (limit: {}) by user: {}",
                eventId, limit, currentUser.getUsername());

        DistributionLogListResponse response = distributionService.getDistributionLogs(eventId, limit, lastEvaluatedKey, currentUser);

        log.info("Retrieved {} distribution logs for event {}, hasMore: {}",
                response.getCount(), eventId, response.getHasMore());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<DistributionLogResponse>> getParticipantLogs(
            @PathVariable Long eventId,
            @PathVariable String bibNumber,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get distribution logs for participant {} in event {} by user: {}",
                bibNumber, eventId, currentUser.getUsername());

        List<DistributionLogResponse> response = distributionService.getParticipantLogs(eventId, bibNumber, currentUser);

        log.info("Retrieved {} distribution logs for participant {} in event {}", response.size(), bibNumber, eventId);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ParticipantDistributionResponse> getDistributionStatus(
            @PathVariable Long eventId,
            @PathVariable String bibNumber,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get distribution status for participant {} in event {} by user: {}",
                bibNumber, eventId, currentUser.getUsername());

        ParticipantDistributionResponse response = distributionService.getDistributionStatus(eventId, bibNumber, currentUser);

        log.info("Retrieved distribution status for participant {} in event {}", bibNumber, eventId);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PendingGoodiesListResponse> getPendingGoodies(
            @PathVariable Long eventId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String lastEvaluatedKey,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get pending goodies list for event {} (limit: {}) by user: {}",
                eventId, limit, currentUser.getUsername());

        PendingGoodiesListResponse response = distributionService.getPendingGoodies(eventId, limit, lastEvaluatedKey, currentUser);

        log.info("Found {} participants with pending goodies for event {}, hasMore: {}",
                response.getCount(), eventId, response.getHasMore());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<DistributionLogListResponse> lookupLogs(
            @PathVariable Long eventId,
            @RequestParam LogSearchType searchType,
            @RequestParam String searchValue,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(required = false) String lastEvaluatedKey,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received log lookup request for event {} with searchType: {}, searchValue: '{}' by user: {}",
                eventId, searchType, searchValue, currentUser.getUsername());

        DistributionLogListResponse response = distributionService.lookupLogs(
                eventId, searchType, searchValue, limit, lastEvaluatedKey, currentUser);

        log.info("Log lookup completed for event {}: found {} logs, hasMore: {}",
                eventId, response.getCount(), response.getHasMore());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<BulkDistributionResponse> bulkCollectBib(
            @PathVariable Long eventId,
            @RequestBody BulkCollectBibRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received bulk bib collection request for event {} with {} bibs by user: {}",
                eventId, request.getBibNumbers().size(), currentUser.getUsername());

        BulkDistributionResponse response = distributionService.bulkCollectBib(eventId, request, currentUser);

        log.info("Bulk bib collection completed for event {}: {} successful, {} failed",
                eventId, response.getSuccessCount(), response.getFailed().size());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<BulkDistributionResponse> bulkDistributeGoodies(
            @PathVariable Long eventId,
            @RequestBody BulkDistributeGoodiesRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received bulk goodies distribution request for event {} with {} items by user: {}",
                eventId, request.getItems().size(), currentUser.getUsername());

        BulkDistributionResponse response = distributionService.bulkDistributeGoodies(eventId, request, currentUser);

        log.info("Bulk goodies distribution completed for event {}: {} successful, {} failed",
                eventId, response.getSuccessCount(), response.getFailed().size());

        return ResponseEntity.ok(response);
    }
}
