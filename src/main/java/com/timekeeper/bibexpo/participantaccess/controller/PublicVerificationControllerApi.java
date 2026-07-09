package com.timekeeper.bibexpo.participantaccess.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.participantaccess.model.dto.response.ParticipantVerificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Public Participant Verification", description = "Public endpoint for participants to verify their details via short link — no authentication required")
@RequestMapping("/api/public/short-links")
public interface PublicVerificationControllerApi {

    @GetMapping("/{shortCode}")
    @Operation(
            summary = "Resolve a participant verification short link",
            description = "Returns the participant's details for the given short code. Participants receive this link before the event to verify their registration details. No authentication required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Participant verification details",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantVerificationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Short link is invalid or has expired",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ParticipantVerificationResponse> resolveShortUrl(@PathVariable String shortCode);
}
