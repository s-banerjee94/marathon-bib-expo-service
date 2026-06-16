package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.response.NotificationListResponse;
import com.timekeeper.bibexpo.model.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Notifications",
        description = """
                In-app notification bell. Notifications are stored server-side (auto-expiring after 30 days) \
                and delivered by **short-polling** — there is no push/SSE connection. Recommended client loop: \
                poll `GET /api/notifications/unread-count` every 20–30s; when the count changes (or the user \
                opens the bell) call `GET /api/notifications` for the list; call the mark-as-read endpoints when \
                the user reads them.

                **What you receive depends on your role** (the server targets each notification at an audience; \
                a notification you didn't qualify for simply never appears in your list):
                - **ROOT / ADMIN** — platform-wide: `EVENT_CREATED` (an organizer made an event), \
                  `EVENT_PUBLISHED`, `EVENT_COMPLETED`.
                - **Organization staff (ORGANIZER_ADMIN / ORGANIZER_USER)** — `EVENT_CANCELLED` and \
                  campaign lifecycle (`CAMPAIGN_COMPLETED` / `CAMPAIGN_FAILED`) for their own organization.
                - **Organization admins (ORGANIZER_ADMIN)** — additionally `IMPORT_COMPLETED` whenever anyone \
                  in their organization completes a CSV import.
                - **The acting user** — their own `IMPORT_COMPLETED` / `IMPORT_FAILED` and `SHORT_URLS_COMPLETED`.

                Each item's `type` drives the icon and `entityType`+`entityId` give the deep-link target — see \
                the `NotificationResponse` schema for the full catalog.""")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/notifications")
public interface NotificationControllerApi {

    /**
     * Returns one page of the current user's notifications, newest first.
     */
    @GetMapping
    @Operation(
            summary = "List my notifications (paginated, newest first)",
            description = """
                    Returns one page of the authenticated user's notifications, newest first. \
                    Pagination is cursor-based: when `hasMore` is true, pass the returned `lastEvaluatedKey` \
                    back as the `cursor` query param to fetch the next page. Cursors are tied to the calling \
                    user; a malformed or foreign cursor returns 400.

                    Each item carries a `type` (drives the frontend icon) and an optional \
                    `entityType` + `entityId` deep-link target (e.g. `EVENT` / `42`) so the client can route \
                    the user to the relevant screen on click.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of notifications",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = NotificationListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid cursor",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<NotificationListResponse> getNotifications(
            @Parameter(description = "Max items per page (1–50)", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Opaque cursor from a previous page's `lastEvaluatedKey`; omit for the first page")
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal User currentUser
    );

    /**
     * Returns the unread count for the bell badge.
     */
    @GetMapping("/unread-count")
    @Operation(
            summary = "Get my unread notification count",
            description = "Returns `{ \"count\": N }` for the bell badge. This is the cheap call to poll every "
                    + "20–30s; only fetch the full list when the count changes or the bell is opened.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread count",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"count\": 3}"))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal User currentUser);

    /**
     * Marks a single notification as read.
     */
    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read",
            description = "Idempotent: marking an unknown or already-read notification still returns 204.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Marked as read"),
            @ApiResponse(responseCode = "400", description = "Invalid notification id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> markAsRead(
            @Parameter(description = "The notification `id` from the list response", required = true)
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser
    );

    /**
     * Marks all of the current user's notifications as read.
     */
    @PatchMapping("/read-all")
    @Operation(summary = "Mark all my notifications as read",
            description = "Clears the unread badge in one call. Returns `{ \"updated\": N }` with how many changed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All marked read",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"updated\": 5}"))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Map<String, Integer>> markAllAsRead(@AuthenticationPrincipal User currentUser);
}
