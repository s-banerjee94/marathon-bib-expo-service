package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.request.BulkDeleteParticipantsRequest;
import com.timekeeper.bibexpo.model.dto.request.CreateParticipantRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateParticipantRequest;
import com.timekeeper.bibexpo.model.dto.response.*;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.ExportField;
import com.timekeeper.bibexpo.model.enums.SearchType;
import com.timekeeper.bibexpo.service.ParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class ParticipantController implements ParticipantControllerApi {

    private final ParticipantService participantService;

    @Override
    public ResponseEntity<ParticipantResponse> createParticipant(
            Long eventId,
            CreateParticipantRequest request,
            User currentUser) {
        log.info("Creating participant for event {} by user {}", eventId, currentUser.getUsername());

        ParticipantResponse response = participantService.createParticipant(eventId, request, currentUser);

        log.info("Created participant with BIB {} for event {}", response.getBibNumber(), eventId);

        return ResponseEntity.status(201).body(response);
    }

    @Override
    public ResponseEntity<ImportParticipantsResponse> importParticipants(
            Long eventId,
            MultipartFile file,
            User currentUser) {
        log.info("Importing participants for event {} by user {}", eventId, currentUser.getUsername());

        ImportParticipantsResponse response = participantService.importParticipantsFromCsv(eventId, file, currentUser);

        log.info("Import completed for event {}. Success: {}, Failed: {}",
                eventId, response.getSuccessCount(), response.getFailureCount());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ParticipantListResponse> getParticipants(
            Long eventId,
            Integer limit,
            String lastEvaluatedKey,
            User currentUser) {
        log.info("Fetching participants for event {} with limit {} by user {}",
                eventId, limit, currentUser.getUsername());

        ParticipantListResponse response = participantService.getParticipantsByEvent(
                eventId, limit, lastEvaluatedKey, currentUser);

        log.info("Fetched {} participants for event {}", response.getCount(), eventId);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ParticipantResponse> getParticipantByBibNumber(
            Long eventId,
            String bibNumber,
            User currentUser) {
        log.info("Fetching participant with bib {} for event {} by user {}",
                bibNumber, eventId, currentUser.getUsername());

        ParticipantResponse response = participantService.getParticipantByBibNumber(
                eventId, bibNumber, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ParticipantResponse> updateParticipant(
            Long eventId, String bibNumber, UpdateParticipantRequest request, User currentUser) {

        log.info("Update participant request - Event: {}, BIB: {}, User: {}",
                eventId, bibNumber, currentUser.getUsername());

        ParticipantResponse response = participantService.updateParticipant(
                eventId, bibNumber, request, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, Long>> getParticipantCount(
            Long eventId,
            User currentUser) {
        log.info("Counting participants for event {} by user {}", eventId, currentUser.getUsername());

        Long count = participantService.getParticipantCount(eventId, currentUser);

        return ResponseEntity.ok(Map.of("count", count));
    }

    @Override
    public ResponseEntity<ImportJobListResponse> getImportJobs(
            Long eventId,
            Integer page,
            Integer size,
            User currentUser) {
        log.info("Fetching import jobs for event {} (page: {}, size: {}) by user {}",
                eventId, page, size, currentUser.getUsername());

        ImportJobListResponse response = participantService.getImportJobsByEvent(
                eventId, page, size, currentUser);

        log.info("Fetched {} import jobs for event {}", response.getTotalCount(), eventId);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ImportJobResponse> getImportJobDetails(
            Long eventId,
            String importId,
            User currentUser) {
        log.info("Fetching import job details for import {} and event {} by user {}",
                importId, eventId, currentUser.getUsername());

        ImportJobResponse response = participantService.getImportJobDetails(
                eventId, importId, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ImportErrorListResponse> getImportErrors(
            Long eventId,
            String importId,
            Integer page,
            Integer size,
            User currentUser) {
        log.info("Fetching import errors for import {} (page: {}, size: {}) by user {}",
                importId, page, size, currentUser.getUsername());

        ImportErrorListResponse response = participantService.getImportErrors(
                eventId, importId, page, size, currentUser);

        log.info("Fetched {} errors (page {}) for import {}", response.getErrors().size(), page, importId);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<DeleteParticipantsResponse> deleteAllParticipants(
            Long eventId,
            User currentUser) {
        log.info("Deleting all participants for event {} by user {}", eventId, currentUser.getUsername());

        DeleteParticipantsResponse response = participantService.deleteAllParticipants(eventId, currentUser);

        log.info("Deleted {} participants for event {}", response.getDeletedCount(), eventId);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<DeleteParticipantsResponse> deleteBulkParticipants(
            Long eventId,
            BulkDeleteParticipantsRequest request,
            User currentUser) {
        log.info("Bulk deleting {} participants for event {} by user {}",
                request.getBibNumbers().size(), eventId, currentUser.getUsername());

        DeleteParticipantsResponse response = participantService.deleteBulkParticipants(
                eventId, request.getBibNumbers(), currentUser);

        log.info("Bulk delete completed for event {}. Deleted: {}, Failed: {}",
                eventId, response.getDeletedCount(), response.getFailedCount());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ParticipantListResponse> searchParticipants(
            Long eventId,
            String searchTerm,
            String raceId,
            String categoryId,
            String gender,
            Integer minAge,
            Integer maxAge,
            String city,
            String country,
            Integer limit,
            String lastEvaluatedKey,
            User currentUser) {

        log.info("Search participants request - Event: {}, searchTerm: '{}', filters: [race={}, category={}, gender={}, age={}-{}, city={}, country={}], limit: {}, user: {}",
                eventId, searchTerm, raceId, categoryId, gender, minAge, maxAge, city, country, limit, currentUser.getUsername());

        ParticipantListResponse response = participantService.searchParticipants(
                eventId, searchTerm, raceId, categoryId, gender, minAge, maxAge, city, country,
                limit, lastEvaluatedKey, currentUser);

        log.info("Search completed for event {}. Found {} participants, hasMore: {}",
                eventId, response.getCount(), response.getHasMore());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<DeleteParticipantsResponse> deleteParticipant(
            Long eventId,
            String bibNumber,
            User currentUser) {
        log.info("Deleting participant with bib {} for event {} by user {}",
                bibNumber, eventId, currentUser.getUsername());

        DeleteParticipantsResponse response = participantService.deleteParticipant(eventId, bibNumber, currentUser);

        log.info("Deleted participant with bib {} for event {}", bibNumber, eventId);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ParticipantListResponse> lookupParticipants(
            Long eventId,
            SearchType searchType,
            String searchValue,
            Integer limit,
            String lastEvaluatedKey,
            User currentUser) {

        log.info("Lookup participants request - Event: {}, searchType: {}, searchValue: '{}', limit: {}, user: {}",
                eventId, searchType, searchValue, limit, currentUser.getUsername());

        ParticipantListResponse response = participantService.lookupParticipants(
                eventId, searchType, searchValue, limit, lastEvaluatedKey, currentUser);

        log.info("Lookup completed for event {}. Found {} participants, hasMore: {}",
                eventId, response.getCount(), response.getHasMore());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<byte[]> exportParticipants(
            Long eventId,
            List<ExportField> fields,
            User currentUser) {

        log.info("Export participants request - Event: {}, fields: {}, user: {}",
                eventId, fields, currentUser.getUsername());

        byte[] csvData = participantService.exportParticipantsToCsv(eventId, fields, currentUser);

        String filename = "participants-event-" + eventId + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(csvData.length);

        log.info("Export completed for event {}. Generated CSV with {} bytes", eventId, csvData.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }

    @Override
    public ResponseEntity<ParticipantStatisticsResponse> getParticipantStatistics(
            Long eventId,
            User currentUser) {

        log.info("Get participant statistics request - Event: {}, user: {}",
                eventId, currentUser.getUsername());

        ParticipantStatisticsResponse response = participantService.getParticipantStatistics(eventId, currentUser);

        return ResponseEntity.ok(response);
    }
}
