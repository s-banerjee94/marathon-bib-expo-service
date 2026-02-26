package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.response.NotificationResponse;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.model.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Tag(name = "Notifications", description = "In-app notification bell — persisted to DB, delivered via SSE")
@SecurityRequirement(name = "bearerAuth")
public interface NotificationControllerApi {

    /**
     * Opens a persistent SSE stream for the authenticated user.
     * The client receives a push event immediately when a batch import completes.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Subscribe to notification stream (SSE)",
            description = "Opens a server-sent event stream. The server pushes a 'notification' event " +
                    "when a batch import job completes. Keep this connection open in the browser. " +
                    "Reconnects automatically via the EventSource API."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream opened"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    SseEmitter subscribe(@AuthenticationPrincipal User currentUser);

    /**
     * Returns paginated notification history for the authenticated user.
     */
    @GetMapping
    @Operation(summary = "Get notification history (paginated)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications retrieved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PageableResponse.class)))
    })
    ResponseEntity<PageableResponse<NotificationResponse>> getNotifications(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser
    );

    /**
     * Returns the unread notification count for the bell badge.
     */
    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count", description = "Returns { \"count\": N } for bell badge.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count returned")
    })
    ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal User currentUser);

    /**
     * Marks a single notification as read.
     */
    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark notification as read")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Marked as read"),
            @ApiResponse(responseCode = "404", description = "Notification not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> markAsRead(
            @Parameter(description = "Notification ID", required = true) @PathVariable Long id,
            @AuthenticationPrincipal User currentUser
    );
}
