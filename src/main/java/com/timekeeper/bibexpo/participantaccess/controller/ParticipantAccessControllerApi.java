package com.timekeeper.bibexpo.participantaccess.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.response.ParticipantDistributionResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.participantaccess.model.dto.request.ScanQrRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

@Tag(name = "Participant Access", description = "Short URL generation and QR code operations for participant self-service and distribution counter scanning")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/events/{eventId}/participant-access")
public interface ParticipantAccessControllerApi {

    @PostMapping("/short-urls")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Generate short URLs for all event participants",
            description = "Kicks off async generation of a short verification code for every participant. The first call generates codes for all participants; later calls only fill in participants that do not yet have one. Returns 202 immediately. "
                    + "UI note: processing happens in the background and can take a while for large events. After the 202 response, show the user an informational pop-up/dialog such as \"Verification links are being generated in the background and will be ready shortly — you can keep working.\" so they do not expect the links to be ready instantly or trigger the action repeatedly."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Generation started in the background. Show the user a pop-up that links are being prepared and will be ready shortly."),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> generateShortUrls(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser);

    @GetMapping("/participants/{bibNumber}/qr")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @Operation(
            summary = "Get QR code PNG for a participant",
            description = "Returns a 300×300 PNG QR code whose payload is AES-GCM encrypted. Only this application can decode it. Intended for delivery via email or WhatsApp to the participant, who shows it at the distribution counter."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "QR code image",
                    content = @Content(mediaType = "image/png")),
            @ApiResponse(responseCode = "404", description = "Event or participant not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<byte[]> getParticipantQr(
            @PathVariable Long eventId,
            @PathVariable String bibNumber,
            @AuthenticationPrincipal User currentUser);

    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER', 'ROLE_DISTRIBUTOR')")
    @Operation(
            summary = "Scan a participant QR code at the distribution counter",
            description = "Decrypts the QR token, verifies it belongs to this event, and returns the participant's full distribution status. Allows distributors to pull up a participant record instantly by scanning."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Participant distribution status",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantDistributionResponse.class))),
            @ApiResponse(responseCode = "400", description = "QR code is invalid or belongs to a different event",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event or participant not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ParticipantDistributionResponse> scanQr(
            @PathVariable Long eventId,
            @RequestBody @Valid ScanQrRequest request,
            @AuthenticationPrincipal User currentUser);
}
