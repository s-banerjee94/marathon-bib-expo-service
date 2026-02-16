package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.request.CreateRaceRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateRaceRequest;
import com.timekeeper.bibexpo.model.dto.response.RaceResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.RaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
        log.info("Received request to get races for event ID: {} by user: {}",
                eventId, currentUser.getUsername());

        List<RaceResponse> races = raceService.getRacesByEventId(eventId, currentUser);

        return ResponseEntity.ok(races);
    }

    @Override
    public ResponseEntity<RaceResponse> getRaceById(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to get race by ID: {} for event ID: {} for user: {}",
                raceId, eventId, currentUser.getUsername());

        RaceResponse response = raceService.getRaceById(eventId, raceId, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RaceResponse> createRace(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateRaceRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to create race: {} for event ID: {} by user: {}",
                request.getRaceName(), eventId, currentUser.getUsername());

        RaceResponse response = raceService.createRace(eventId, request, currentUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<RaceResponse> updateRace(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @Valid @RequestBody UpdateRaceRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to update race with ID: {} for event ID: {} by user: {}",
                raceId, eventId, currentUser.getUsername());

        RaceResponse response = raceService.updateRace(eventId, raceId, request, currentUser);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteRace(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @AuthenticationPrincipal User currentUser) {
        log.info("Received request to delete race with ID: {} for event ID: {} by user: {}",
                raceId, eventId, currentUser.getUsername());

        raceService.deleteRace(eventId, raceId, currentUser);

        return ResponseEntity.noContent().build();
    }
}
