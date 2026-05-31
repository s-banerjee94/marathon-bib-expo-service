package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.dto.response.AuditLogListResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

/**
 * API interface for the audit log (Recent Activity feed).
 * Backed by DynamoDB with a 15-day TTL — older entries are auto-deleted.
 */
@Tag(
        name = "Audit Logs",
        description = "<p>Recent activity feed — audit trail of CREATE / UPDATE / DELETE / STATUS_CHANGE / LOGIN / GENERATE / IMPORT actions across the system. " +
                "Backed by DynamoDB with a <strong>15-day TTL</strong> (older entries auto-delete). " +
                "Every read is a single direct DynamoDB <code>Query</code> — no scans, no in-app filtering.</p>"
)
@RequestMapping("/api/audit-logs")
@SecurityRequirement(name = "bearerAuth")
public interface AuditLogControllerApi {

    @Operation(
            summary = "Get audit log entries (recent feed + filtered search)",
            description = """
                    <h3>Overview</h3>
                    <p>Returns audit log entries <strong>newest first</strong> with cursor-based pagination.
                    A single endpoint covers two use cases:</p>
                    <ul>
                      <li><strong>Recent Activity feed</strong> — call with no filters (or just <code>organizationId</code>) to get the latest 50 rows.</li>
                      <li><strong>Filtered search</strong> — add one filter dimension (<code>action</code>, <code>entityType</code>, or <code>username</code>) and/or a date range to narrow.</li>
                    </ul>
                    <p>Every call is exactly one direct DynamoDB <code>Query</code>. There is no <code>FilterExpression</code>,
                    no <code>Scan</code>, and no in-app filtering — so the page size you ask for is the page size you get.</p>

                    <h3>Scope (who sees what)</h3>
                    <table border="1" cellpadding="6" cellspacing="0">
                      <thead><tr><th>Caller</th><th>Param</th><th>Returns</th></tr></thead>
                      <tbody>
                        <tr><td>ORGANIZER_ADMIN, ORGANIZER_USER</td><td><em>any value of <code>organizationId</code> is ignored</em></td><td>Their own organization only</td></tr>
                        <tr><td>ROOT, ADMIN</td><td>omit <code>organizationId</code></td><td>Cross-org feed — every org + system events, sorted by time</td></tr>
                        <tr><td>ROOT, ADMIN</td><td><code>organizationId=&lt;id&gt;</code></td><td>Just that organization</td></tr>
                        <tr><td>ROOT, ADMIN</td><td><code>organizationId=0</code></td><td>System events only (ROOT-managed admin actions, actors without an org, LOGINs without an org)</td></tr>
                      </tbody>
                    </table>

                    <h3>Filter dimensions</h3>
                    <p>You may pass at most <strong>one</strong> of <code>action</code>, <code>entityType</code>, or <code>username</code> per call (each is backed by a different DynamoDB index). Passing two or more of those dimensions in the same call returns <strong>HTTP 400</strong>. The <code>username</code> filter is an exact, case-sensitive match (e.g. <code>rahul.sharma</code>).</p>

                    <h4>All <code>action</code> values</h4>
                    <table border="1" cellpadding="6" cellspacing="0">
                      <thead><tr><th>Value</th><th>Meaning</th><th>Emitted when</th></tr></thead>
                      <tbody>
                        <tr><td><code>CREATE</code></td><td>A new entity was created</td><td>create-org / create-event / create-race / create-category / create-user / create-sms-template / create-sms-campaign</td></tr>
                        <tr><td><code>UPDATE</code></td><td>An existing entity was modified</td><td>update endpoints for org / event / race / category / user / sms-template / sms-campaign</td></tr>
                        <tr><td><code>DELETE</code></td><td>An entity was deleted</td><td>delete endpoints for event / race / category / user / sms-template / sms-campaign</td></tr>
                        <tr><td><code>STATUS_CHANGE</code></td><td>An entity's status / enabled flag flipped</td><td>enable-or-disable on org / event / user, plus disarm on sms-campaign (ACTIVE → DRAFT)</td></tr>
                        <tr><td><code>LOGIN</code></td><td>A user successfully authenticated</td><td><code>POST /api/auth/login</code> after credentials accepted</td></tr>
                        <tr><td><code>GENERATE</code></td><td>A short-lived artifact was generated</td><td>Participant-access verification short-URLs (QR / SMS) issued in bulk</td></tr>
                        <tr><td><code>IMPORT</code></td><td>A bulk CSV participant import was launched</td><td><code>POST /api/events/{eventId}/participants/batch-import</code> after the mapping is accepted and the job starts</td></tr>
                      </tbody>
                    </table>

                    <h4>All <code>entityType</code> values</h4>
                    <table border="1" cellpadding="6" cellspacing="0">
                      <thead><tr><th>Value</th><th>What it refers to</th><th>Actions that can target it</th></tr></thead>
                      <tbody>
                        <tr><td><code>ORGANIZATION</code></td><td>A marathon organizer tenant</td><td>CREATE, UPDATE, STATUS_CHANGE</td></tr>
                        <tr><td><code>USER</code></td><td>A platform user account (ROOT / ADMIN / ORGANIZER_ADMIN / ORGANIZER_USER)</td><td>CREATE, UPDATE, STATUS_CHANGE, DELETE, LOGIN</td></tr>
                        <tr><td><code>EVENT</code></td><td>A marathon event under an organization</td><td>CREATE, UPDATE, STATUS_CHANGE, DELETE</td></tr>
                        <tr><td><code>RACE</code></td><td>A race inside an event</td><td>CREATE, UPDATE, DELETE</td></tr>
                        <tr><td><code>CATEGORY</code></td><td>An age / gender category inside a race</td><td>CREATE, UPDATE, DELETE</td></tr>
                        <tr><td><code>SMS_TEMPLATE</code></td><td>A reusable SMS message template</td><td>CREATE, UPDATE, DELETE</td></tr>
                        <tr><td><code>SMS_CAMPAIGN</code></td><td>A scheduled or trigger-based SMS send</td><td>CREATE, UPDATE, STATUS_CHANGE, DELETE</td></tr>
                        <tr><td><code>VERIFICATION_LINK</code></td><td>Short URLs (QR / SMS) generated for participant self-verification</td><td>GENERATE</td></tr>
                        <tr><td><code>PARTICIPANT</code></td><td>Participant records of an event (currently via bulk CSV import)</td><td>IMPORT</td></tr>
                      </tbody>
                    </table>
                    <p><em>Note:</em> Individual participant create / update / delete actions and distribution events (bib collected, goodies given) are <strong>not</strong> recorded in this audit log — distribution has its own dedicated log. Bulk CSV imports <strong>are</strong> recorded as <code>entityType=PARTICIPANT</code>, <code>action=IMPORT</code>, with the <code>entityId</code> set to the event id and the <code>entityLabel</code> set to the event name.</p>

                    <h3>Date range (optional, additive)</h3>
                    <ul>
                      <li><code>from</code> — inclusive lower bound, ISO-8601 instant (e.g. <code>2026-05-01T00:00:00Z</code>)</li>
                      <li><code>to</code> — inclusive upper bound, ISO-8601 instant</li>
                      <li>Either, both, or neither may be supplied; date range does <strong>not</strong> count as a filter dimension</li>
                      <li>If <code>from</code> is after <code>to</code>, returns <strong>HTTP 400</strong></li>
                    </ul>

                    <h3>Pagination</h3>
                    <ul>
                      <li><code>limit</code> — page size, default <strong>50</strong></li>
                      <li><code>lastEvaluatedKey</code> — opaque cursor returned by the previous response. Omit on the first page.</li>
                      <li>Response carries <code>hasMore</code> (boolean) and the <code>lastEvaluatedKey</code> to use next.</li>
                    </ul>

                    <h3>All supported query combinations</h3>
                    <p>Each row below is one valid call. <em>Optional</em> date range (<code>from</code> / <code>to</code>) may be added to any row without changing what it does.</p>
                    <table border="1" cellpadding="6" cellspacing="0">
                      <thead><tr><th>#</th><th>Query string</th><th>Returns</th><th>Allowed for</th></tr></thead>
                      <tbody>
                        <tr><td>1</td><td><code>(no params)</code></td><td>Recent feed: ROOT/ADMIN get cross-org; org users get their own org</td><td>All roles</td></tr>
                        <tr><td>2</td><td><code>?organizationId=5</code></td><td>Org 5's feed, newest 50</td><td>ROOT/ADMIN (org users: param ignored)</td></tr>
                        <tr><td>3</td><td><code>?organizationId=0</code></td><td>System events only</td><td>ROOT/ADMIN</td></tr>
                        <tr><td>4</td><td><code>?action=DELETE</code></td><td>Every DELETE in scope</td><td>All roles</td></tr>
                        <tr><td>5</td><td><code>?entityType=EVENT</code></td><td>Every EVENT-entity audit in scope</td><td>All roles</td></tr>
                        <tr><td>6</td><td><code>?username=rahul.sharma</code></td><td>Every action by rahul.sharma in scope</td><td>All roles</td></tr>
                        <tr><td>7</td><td><code>?organizationId=5&amp;action=CREATE</code></td><td>Every CREATE in org 5</td><td>ROOT/ADMIN</td></tr>
                        <tr><td>8</td><td><code>?organizationId=5&amp;entityType=USER</code></td><td>Every USER-entity audit in org 5</td><td>ROOT/ADMIN</td></tr>
                        <tr><td>9</td><td><code>?organizationId=5&amp;username=rahul.sharma</code></td><td>rahul.sharma's actions inside org 5</td><td>ROOT/ADMIN</td></tr>
                        <tr><td>10</td><td><code>?from=2026-05-01T00:00:00Z&amp;to=2026-05-15T23:59:59Z</code></td><td>Recent feed limited to that window</td><td>All roles</td></tr>
                        <tr><td>11</td><td><code>?action=DELETE&amp;from=2026-05-20T00:00:00Z</code></td><td>DELETEs since 2026-05-20</td><td>All roles</td></tr>
                        <tr><td>12</td><td><code>?limit=20&amp;lastEvaluatedKey=&lt;cursor&gt;</code></td><td>Page 2 (size 20) of the previous call</td><td>All roles</td></tr>
                      </tbody>
                    </table>

                    <h3>Rejected combinations (return HTTP 400)</h3>
                    <ul>
                      <li><code>?action=DELETE&amp;entityType=EVENT</code> — two filter dimensions</li>
                      <li><code>?action=DELETE&amp;username=rahul.sharma</code> — two filter dimensions</li>
                      <li><code>?entityType=USER&amp;username=rahul.sharma</code> — two filter dimensions</li>
                      <li><code>?from=2026-06-01T00:00:00Z&amp;to=2026-05-01T00:00:00Z</code> — start after end</li>
                    </ul>

                    <h3>Worked examples (full URLs)</h3>
                    <ol>
                      <li><strong>ROOT opens the global Recent Activity dashboard:</strong><br>
                          <code>GET /api/audit-logs</code></li>
                      <li><strong>ROOT investigates one organizer's full history this month:</strong><br>
                          <code>GET /api/audit-logs?username=jane.doe&amp;from=2026-05-01T00:00:00Z</code></li>
                      <li><strong>ROOT reviews admin-management actions:</strong><br>
                          <code>GET /api/audit-logs?organizationId=0&amp;action=CREATE</code></li>
                      <li><strong>Organizer admin paginates to page 2:</strong><br>
                          <code>GET /api/audit-logs?lastEvaluatedKey=eyJvcmdhbml6...</code></li>
                      <li><strong>Show every DELETE across the system this week:</strong><br>
                          <code>GET /api/audit-logs?action=DELETE&amp;from=2026-05-21T00:00:00Z</code></li>
                    </ol>

                    <h3>Response shape (top-level)</h3>
                    <ul>
                      <li><code>items</code> — array of <code>AuditLogResponse</code>, newest first</li>
                      <li><code>count</code> — number of items in this page</li>
                      <li><code>hasMore</code> — <code>true</code> if more pages exist</li>
                      <li><code>lastEvaluatedKey</code> — opaque cursor for the next call (<code>null</code> when <code>hasMore</code> is <code>false</code>)</li>
                    </ul>

                    <h3>Errors</h3>
                    <ul>
                      <li><strong>400 Bad Request</strong> — more than one of <code>action</code> / <code>entityType</code> / <code>username</code>, or <code>from</code> after <code>to</code></li>
                      <li><strong>401 Unauthorized</strong> — missing or invalid bearer token</li>
                      <li><strong>403 Forbidden</strong> — caller's role cannot access this endpoint</li>
                    </ul>

                    <h3>Operational notes</h3>
                    <ul>
                      <li>Retention is <strong>15 days</strong> — older rows are auto-deleted by DynamoDB TTL. Queries with a <code>from</code> earlier than that will simply return less data.</li>
                      <li>Writes are dual-written (per-org partition + cross-org partition) so the ROOT/ADMIN unified feed is a single sorted Query with no merge cost on read.</li>
                      <li>Audit writes are <em>fire-and-forget async</em>; expect a small delay (sub-second) between the business action and the entry appearing here.</li>
                    </ul>
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit log entries retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuditLogListResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Bad request — more than one filter dimension was passed, or <code>from</code> is after <code>to</code>",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller's role cannot access this endpoint",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    ResponseEntity<AuditLogListResponse> getAuditLogs(
            @Parameter(
                    description = "ROOT/ADMIN only. Omit for the cross-org feed; pass an org id to drill into one org; pass <code>0</code> for system events only. Ignored for organizer roles (they are auto-scoped to their own org).",
                    example = "5"
            )
            @RequestParam(required = false) Long organizationId,

            @Parameter(
                    description = "Inclusive window start, ISO-8601 instant. Combine with any filter or use alone to bound the recent feed.",
                    example = "2026-05-01T00:00:00Z"
            )
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,

            @Parameter(
                    description = "Inclusive window end, ISO-8601 instant. Must be on or after <code>from</code>.",
                    example = "2026-05-27T23:59:59Z"
            )
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,

            @Parameter(
                    description = "Filter by action type. Mutually exclusive with <code>entityType</code> and <code>username</code>.",
                    example = "DELETE"
            )
            @RequestParam(required = false) AuditAction action,

            @Parameter(
                    description = "Filter by entity type. Mutually exclusive with <code>action</code> and <code>username</code>.",
                    example = "EVENT"
            )
            @RequestParam(required = false) AuditEntityType entityType,

            @Parameter(
                    description = "Filter by the acting user's username (exact match, case-sensitive). Mutually exclusive with <code>action</code> and <code>entityType</code>.",
                    example = "rahul.sharma"
            )
            @RequestParam(required = false) String username,

            @Parameter(description = "Page size (default 50). Applies to the returned page only — DynamoDB does not over-read.", example = "50")
            @RequestParam(defaultValue = "50") int limit,

            @Parameter(description = "Opaque pagination cursor from the previous response's <code>lastEvaluatedKey</code>. Omit on the first page.")
            @RequestParam(required = false) String lastEvaluatedKey,

            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser
    );
}
