package com.timekeeper.bibexpo.participant.controller;

import com.timekeeper.bibexpo.participant.exception.BibNumberAlreadyExistsException;
import com.timekeeper.bibexpo.participant.exception.ChipNumberAlreadyExistsException;
import com.timekeeper.bibexpo.participant.exception.ParticipantDeletionNotAllowedException;
import com.timekeeper.bibexpo.participant.exception.ParticipantModificationNotAllowedException;
import com.timekeeper.bibexpo.participant.model.dto.request.BulkDeleteParticipantsRequest;
import com.timekeeper.bibexpo.participant.model.dto.request.CreateParticipantRequest;
import com.timekeeper.bibexpo.participant.model.dto.request.UpdateParticipantRequest;
import com.timekeeper.bibexpo.participant.model.dto.response.DeleteParticipantsResponse;
import com.timekeeper.bibexpo.participant.model.dto.response.ParticipantListResponse;
import com.timekeeper.bibexpo.participant.model.dto.response.ParticipantResponse;
import com.timekeeper.bibexpo.participant.model.enums.ExportField;
import com.timekeeper.bibexpo.participant.model.enums.SearchType;
import com.timekeeper.bibexpo.participant.service.ParticipantExportService;
import com.timekeeper.bibexpo.participant.service.ParticipantService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class ParticipantController implements ParticipantControllerApi {

    private final ParticipantService participantService;
    private final ParticipantExportService participantExportService;

    @Override
    public ResponseEntity<ParticipantResponse> createParticipant(
            Long eventId,
            CreateParticipantRequest request,
            User currentUser) {
        return ResponseEntity.status(201).body(participantService.createParticipant(eventId, request, currentUser));
    }

    @Override
    public ResponseEntity<ParticipantListResponse> getParticipants(
            Long eventId,
            Integer limit,
            String lastEvaluatedKey,
            User currentUser) {
        return ResponseEntity.ok(participantService.getParticipantsByEvent(eventId, limit, lastEvaluatedKey, currentUser));
    }

    @Override
    public ResponseEntity<ParticipantResponse> getParticipantByBibNumber(
            Long eventId,
            String bibNumber,
            User currentUser) {
        return ResponseEntity.ok(participantService.getParticipantByBibNumber(eventId, bibNumber, currentUser));
    }

    @Override
    public ResponseEntity<ParticipantResponse> updateParticipant(
            Long eventId, String bibNumber, UpdateParticipantRequest request, User currentUser) {
        return ResponseEntity.ok(participantService.updateParticipant(eventId, bibNumber, request, currentUser));
    }

    @Override
    public ResponseEntity<Map<String, Long>> getParticipantCount(
            Long eventId,
            User currentUser) {
        return ResponseEntity.ok(Map.of("count", participantService.getParticipantCount(eventId, currentUser)));
    }

    @Override
    public ResponseEntity<DeleteParticipantsResponse> deleteAllParticipants(
            Long eventId,
            User currentUser) {
        return ResponseEntity.ok(participantService.deleteAllParticipants(eventId, currentUser));
    }

    @Override
    public ResponseEntity<DeleteParticipantsResponse> deleteBulkParticipants(
            Long eventId,
            BulkDeleteParticipantsRequest request,
            User currentUser) {
        return ResponseEntity.ok(participantService.deleteBulkParticipants(eventId, request.getBibNumbers(), currentUser));
    }

    @Override
    public ResponseEntity<DeleteParticipantsResponse> deleteParticipant(
            Long eventId,
            String bibNumber,
            User currentUser) {
        return ResponseEntity.ok(participantService.deleteParticipant(eventId, bibNumber, currentUser));
    }

    @Override
    public ResponseEntity<ParticipantListResponse> lookupParticipants(
            Long eventId,
            SearchType searchType,
            String searchValue,
            Integer limit,
            String lastEvaluatedKey,
            User currentUser) {
        return ResponseEntity.ok(participantService.lookupParticipants(
                eventId, searchType, searchValue, limit, lastEvaluatedKey, currentUser));
    }

    @Override
    public ResponseEntity<byte[]> exportParticipants(
            Long eventId,
            List<ExportField> fields,
            User currentUser) {
        byte[] csvData = participantExportService.exportParticipantsToCsv(eventId, fields, currentUser);

        String filename = "participants-event-" + eventId + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(csvData.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }

    @ExceptionHandler(ParticipantDeletionNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleParticipantDeletionNotAllowed(
            ParticipantDeletionNotAllowedException ex, WebRequest request) {
        logRejected(HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }

    @ExceptionHandler(ParticipantModificationNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleParticipantModificationNotAllowed(
            ParticipantModificationNotAllowedException ex, WebRequest request) {
        logRejected(HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
    }

    @ExceptionHandler(BibNumberAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleBibNumberAlreadyExists(
            BibNumberAlreadyExistsException ex, WebRequest request) {
        logRejected(HttpStatus.CONFLICT, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    @ExceptionHandler(ChipNumberAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleChipNumberAlreadyExists(
            ChipNumberAlreadyExistsException ex, WebRequest request) {
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
