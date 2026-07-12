package com.timekeeper.bibexpo.demo.controller;

import com.timekeeper.bibexpo.demo.model.dto.response.DemoSessionResponse;
import com.timekeeper.bibexpo.demo.model.dto.response.DemoSessionStatusResponse;
import com.timekeeper.bibexpo.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Public Landing Demo", description = "Public endpoints powering the landing-page live QR demo — fabricated data only, no authentication required")
@RequestMapping("/api/public/demo/sessions")
public interface DemoSessionControllerApi {

    @PostMapping
    @Operation(
            summary = "Create a demo session",
            description = "Creates a session with a fabricated runner for the landing-page bib card. The returned code is encoded into the bib QR as {origin}/demo/{code}. Sessions are in-memory, short-lived, and never touch real data."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DemoSessionResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit or global live-session cap exceeded",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<DemoSessionResponse> createSession(HttpServletRequest request);

    @GetMapping("/{code}")
    @Operation(
            summary = "Fetch a demo session",
            description = "Returns the fabricated runner for the phone's mini distributor view and marks the session SCANNED on first read."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session details",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DemoSessionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Unknown code",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "Session expired",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<DemoSessionResponse> getSession(@PathVariable String code);

    @PostMapping("/{code}/collect")
    @Operation(
            summary = "Mark a demo session collected",
            description = "Single-use collect from the phone view; the first tap wins and flips the desktop's polled status to COLLECTED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Collected",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DemoSessionStatusResponse.class))),
            @ApiResponse(responseCode = "404", description = "Unknown code",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Already collected",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "Session expired",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<DemoSessionStatusResponse> collectSession(@PathVariable String code, HttpServletRequest request);

    @GetMapping("/{code}/status")
    @Operation(
            summary = "Poll a demo session's status",
            description = "Cheap status-only read, polled by the desktop while the QR is on screen and the tab is visible."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current status",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DemoSessionStatusResponse.class))),
            @ApiResponse(responseCode = "404", description = "Unknown code",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "Session expired",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<DemoSessionStatusResponse> getSessionStatus(@PathVariable String code);

    @GetMapping(value = "/{code}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream demo session status changes",
            description = "SSE stream the desktop keeps open instead of polling: the current status is sent on connect, changes are pushed instantly, and the stream closes on COLLECTED or session expiry. Polling /status remains the fallback."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream of status events",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
            @ApiResponse(responseCode = "404", description = "Unknown code",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "Session expired",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SseEmitter> streamSessionEvents(@PathVariable String code);
}
