package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.request.AiChatRequest;
import com.timekeeper.bibexpo.model.dto.response.AiChatHistoryResponse;
import com.timekeeper.bibexpo.model.dto.response.AiChatResponse;
import com.timekeeper.bibexpo.model.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * In-app AI assistant. Send a natural-language message and get a reply; the assistant may call
 * application tools on the signed-in user's behalf, respecting the same role-based access as the
 * REST API. Not available to the DISTRIBUTOR role.
 */
@Tag(name = "AI Assistant", description = "Conversational in-app assistant")
@SecurityRequirement(name = "bearerAuth")
public interface AiChatControllerApi {

    /**
     * Send one chat turn to the assistant and receive its reply.
     */
    @Operation(summary = "Chat with the AI assistant")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @PostMapping
    ResponseEntity<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    /**
     * Load the signed-in user's stored conversation so the client can repaint it, for example
     * after a page refresh.
     */
    @Operation(summary = "Get my assistant conversation history")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @GetMapping
    ResponseEntity<AiChatHistoryResponse> history(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);

    /**
     * Clear the signed-in user's conversation history, starting the assistant fresh.
     */
    @Operation(summary = "Reset my assistant conversation")
    @PreAuthorize("hasAnyRole('ROLE_ROOT', 'ROLE_ADMIN', 'ROLE_ORGANIZER_ADMIN', 'ROLE_ORGANIZER_USER')")
    @DeleteMapping
    ResponseEntity<Void> reset(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser);
}
