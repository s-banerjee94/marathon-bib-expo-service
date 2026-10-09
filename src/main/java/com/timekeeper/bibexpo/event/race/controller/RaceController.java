package com.timekeeper.bibexpo.event.race.controller;

import com.timekeeper.bibexpo.event.race.exception.RaceAlreadyExistsException;
import com.timekeeper.bibexpo.event.race.exception.RaceDeletionNotAllowedException;
import com.timekeeper.bibexpo.event.race.model.dto.request.CreateRaceRequest;
import com.timekeeper.bibexpo.event.race.model.dto.request.UpdateRaceRequest;
import com.timekeeper.bibexpo.event.race.model.dto.response.RaceResponse;
import com.timekeeper.bibexpo.event.race.service.RaceService;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/races")
@RequiredArgsConstructor
@Slf4j
public class RaceController implements RaceControllerApi {

    private final RaceService raceService;

    @Override
    public ResponseEntity<List<RaceResponse>> getRacesByEventId(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        List<RaceResponse> races = raceService.getRacesByEventId(eventId, currentUser);

        return ResponseEntity.ok(races);
    }

    @Override
    public ResponseEntity<RaceResponse> getRaceById(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @AuthenticationPrincipal User currentUser) {
        RaceResponse response = raceService.getRaceById(eventId, raceId, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RaceResponse> createRace(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateRaceRequest request,
            @AuthenticationPrincipal User currentUser) {
        RaceResponse response = raceService.createRace(eventId, request, currentUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<RaceResponse> updateRace(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @Valid @RequestBody UpdateRaceRequest request,
            @AuthenticationPrincipal User currentUser) {
        RaceResponse response = raceService.updateRace(eventId, raceId, request, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteRace(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @AuthenticationPrincipal User currentUser) {
        raceService.deleteRace(eventId, raceId, currentUser);

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(RaceAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleRaceAlreadyExists(
            RaceAlreadyExistsException ex, WebRequest request) {
        logRejected(HttpStatus.CONFLICT, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    @ExceptionHandler(RaceDeletionNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleRaceDeletionNotAllowed(
            RaceDeletionNotAllowedException ex, WebRequest request) {
        logRejected(HttpStatus.BAD_REQUEST, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request));
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
