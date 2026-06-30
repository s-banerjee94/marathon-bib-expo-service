package com.timekeeper.bibexpo.ai.agent.controller;

import com.timekeeper.bibexpo.ai.agent.dto.request.AgentChatRequest;
import com.timekeeper.bibexpo.ai.agent.dto.request.AgentResumeRequest;
import com.timekeeper.bibexpo.ai.agent.exception.AiAgentBusyException;
import com.timekeeper.bibexpo.ai.agent.exception.AiAgentException;
import com.timekeeper.bibexpo.ai.agent.exception.AiUsageLimitException;
import com.timekeeper.bibexpo.ai.agent.dto.response.AgentUsageResponse;
import com.timekeeper.bibexpo.ai.agent.service.AiAgentClient;
import com.timekeeper.bibexpo.ai.agent.service.AiUsageService;
import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentChatController implements AgentChatControllerApi {

    private final AiAgentClient aiAgentClient;
    private final AiUsageService aiUsageService;

    @Override
    public ResponseEntity<JsonNode> chat(AgentChatRequest request, @AuthenticationPrincipal User currentUser) {
        aiUsageService.checkAllowed(currentUser);
        return ResponseEntity.ok(aiAgentClient.chat(currentUser, request.getMessage(), request.getMode()));
    }

    @Override
    public ResponseEntity<JsonNode> resume(AgentResumeRequest request, @AuthenticationPrincipal User currentUser) {
        aiUsageService.checkAllowed(currentUser);
        return ResponseEntity.ok(aiAgentClient.resume(currentUser, request.getDecisions(), request.getMode()));
    }

    @Override
    public ResponseEntity<Void> resetConversation(@AuthenticationPrincipal User currentUser) {
        aiAgentClient.resetConversation(currentUser);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AgentUsageResponse> usage(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(aiUsageService.getUsage(currentUser));
    }

    @Override
    public ResponseEntity<JsonNode> history(Integer cursor, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(aiAgentClient.history(currentUser, cursor));
    }

    @ExceptionHandler(AiUsageLimitException.class)
    ResponseEntity<ErrorResponse> handleAiUsageLimit(AiUsageLimitException ex, WebRequest request) {
        log.info("AI usage limit reached: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    @ExceptionHandler(AiAgentBusyException.class)
    ResponseEntity<ErrorResponse> handleAiAgentBusy(AiAgentBusyException ex, WebRequest request) {
        log.info("AI agent busy (upstream rate limit): {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    @ExceptionHandler(AiAgentException.class)
    ResponseEntity<ErrorResponse> handleAiAgentUnavailable(AiAgentException ex, WebRequest request) {
        log.warn("AI agent unavailable: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Service Unavailable")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
}
