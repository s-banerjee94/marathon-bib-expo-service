package com.timekeeper.bibexpo.identity.controller;

import com.timekeeper.bibexpo.identity.model.dto.response.SessionResponse;
import com.timekeeper.bibexpo.shared.error.ErrorResponse;
import com.timekeeper.bibexpo.user.model.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * API interface for a user's own signed-in devices.
 *
 * <p>Every endpoint acts on the caller's own sessions only — there is no way to read or end
 * another account's devices here. How many devices a user may hold at once is a per-role limit;
 * a login past it signs out their least recently used device automatically.
 */
@Tag(name = "Sessions", description = "APIs for viewing and signing out your own devices")
@RequestMapping("/api/sessions")
@SecurityRequirement(name = "bearerAuth")
public interface SessionControllerApi {

    @Operation(
            summary = "List my signed-in devices",
            description = """
                    Returns every device currently signed in to the calling account, most recently \
                    used first, with the address, browser and operating system each one reported at \
                    login. The device making the request is flagged with current=true. \
                    The least recently used device is the one dropped when a new login exceeds the \
                    account's device limit."""
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The caller's signed-in devices",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = SessionResponse.class))
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping
    ResponseEntity<List<SessionResponse>> listMySessions(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser,
            @Parameter(hidden = true) HttpServletRequest httpRequest);

    @Operation(
            summary = "Sign out one of my devices",
            description = """
                    Signs out a single device by its sessionId, taken from the device list. \
                    Only the caller's own devices can be signed out; an unknown sessionId is \
                    reported as not found rather than revealing whose it is. Signing out the \
                    current device is allowed and behaves like a logout."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Device signed out"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(
                    responseCode = "404",
                    description = "No such device is signed in to this account",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @DeleteMapping("/{sessionId}")
    ResponseEntity<Void> endMySession(
            @Parameter(description = "Session id from the device list", required = true)
            @PathVariable String sessionId,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    @Operation(
            summary = "Sign out my other devices",
            description = """
                    Signs out every device on the account except the one making the request, which \
                    stays signed in. Use this after a device is lost, or to clear devices you no \
                    longer recognise in the list."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Other devices signed out"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @DeleteMapping
    ResponseEntity<Void> endMyOtherSessions(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser,
            @Parameter(hidden = true) HttpServletRequest httpRequest);
}
