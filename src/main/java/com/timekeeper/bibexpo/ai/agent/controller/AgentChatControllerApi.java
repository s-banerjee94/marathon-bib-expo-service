package com.timekeeper.bibexpo.ai.agent.controller;

import com.timekeeper.bibexpo.ai.agent.dto.request.AgentChatRequest;
import com.timekeeper.bibexpo.ai.agent.dto.request.AgentResumeRequest;
import com.timekeeper.bibexpo.ai.agent.dto.response.AgentChatResponse;
import com.timekeeper.bibexpo.ai.agent.dto.response.AgentHistoryResponse;
import com.timekeeper.bibexpo.ai.agent.dto.response.AgentUsageResponse;
import com.timekeeper.bibexpo.model.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Conversational AI agent backed by the Python LangGraph service. Spring authenticates the user,
 * mints a short-lived MCP-scoped token, and proxies the message to the agent, which may call
 * application tools on the user's behalf under the same role-based access as the REST API. Not
 * available to the DISTRIBUTOR role.
 */
@Tag(name = "AI Agent", description = """
        Conversational AI assistant backed by the Python LangGraph service. Spring authenticates the
        user, mints a short-lived MCP-scoped token and proxies the message to the agent, which may call
        application tools on the user's behalf under the same role-based access as the REST API. Not
        available to the DISTRIBUTOR role.

        **Frontend flow**

        1. POST `/chat` with the user's `message`. The server keeps one conversation per user, so you
        never resend history.
        2. The reply is either `status = complete` (render `reply` as **markdown**) or
        `status = needs_approval` (the agent wants to run the writes listed in `pending`).
        3. For `needs_approval`, render each `pending[].summary` (markdown) and a button per
        `pending[].actions` entry (approve / edit / reject). Then POST `/resume` with one decision per
        pending action, in the same order: `approve` runs it, `reject` cancels it, and `edit` carries the
        user's change in plain words as `message` (the agent revises and asks for confirmation again).
        Repeat until you get `complete`.

        All replies are markdown; render them with a markdown component. `mode` is **required on every
        call** (both `/chat` and `/resume`); send the same mode the conversation is using. `auto` = run
        writes without asking, `agent` = let the agent decide, `ask` = always pause for approval.

        Errors: 429 when the caller's daily AI token budget is exhausted (retry tomorrow); 503 if the
        agent service is unreachable.
        """)
@SecurityRequirement(name = "bearerAuth")
public interface AgentChatControllerApi {

    /**
     * Send one message to the agent. The response is either a final reply or a request to approve
     * one or more pending write actions; resume those via {@link #resume}.
     */
    @Operation(summary = "Send a message to the AI agent")
    @ApiResponse(responseCode = "200",
            description = "A final reply (status=complete) or an approval request (status=needs_approval).",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AgentChatResponse.class)))
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @PostMapping("/chat")
    ResponseEntity<JsonNode> chat(
            @Valid @RequestBody AgentChatRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    /**
     * Resume a conversation that paused for human approval, supplying one decision per pending action.
     */
    @Operation(summary = "Resume the AI agent after an approval pause")
    @ApiResponse(responseCode = "200",
            description = "The continued reply (status=complete) or a further approval request (status=needs_approval).",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AgentChatResponse.class)))
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @PostMapping("/resume")
    ResponseEntity<JsonNode> resume(
            @Valid @RequestBody AgentResumeRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    /**
     * Start a new conversation by deleting the current user's chat memory. The next message begins
     * with empty history. Wipes only the caller's own conversation.
     */
    @Operation(summary = "Start a new conversation (clear chat memory)")
    @ApiResponse(responseCode = "204", description = "Conversation memory cleared; the next message starts fresh.")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @DeleteMapping("/conversation")
    ResponseEntity<Void> resetConversation(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    /**
     * The caller's AI token budget for today — tokens used, the role cap, what remains, and when it
     * resets (next UTC midnight). A read-only meter; never consumes budget and never returns 429.
     */
    @Operation(summary = "Get the caller's daily AI token usage")
    @ApiResponse(responseCode = "200", description = "Current usage: used, limit, remaining, and reset time.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AgentUsageResponse.class)))
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @GetMapping("/usage")
    ResponseEntity<AgentUsageResponse> usage(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    /**
     * Fetch a page of the caller's conversation history, newest first (25 per page), so the UI can
     * restore the chat after a reload. Load older messages by passing the previous response's
     * {@code nextCursor}; stop when {@code hasMore} is false.
     */
    @Operation(summary = "Fetch a page of the caller's conversation history")
    @ApiResponse(responseCode = "200",
            description = "A page of prior messages with nextCursor and hasMore for loading older messages.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AgentHistoryResponse.class)))
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @GetMapping("/history")
    ResponseEntity<JsonNode> history(
            @Parameter(description = "nextCursor from a previous page; omit for the newest page.")
            @RequestParam(required = false) Integer cursor,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
