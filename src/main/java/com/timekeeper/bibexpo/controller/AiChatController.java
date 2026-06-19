package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.request.AiChatRequest;
import com.timekeeper.bibexpo.model.dto.response.AiChatHistoryResponse;
import com.timekeeper.bibexpo.model.dto.response.AiChatResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class AiChatController implements AiChatControllerApi {

    private final AiChatService aiChatService;

    @Override
    public ResponseEntity<AiChatResponse> chat(AiChatRequest request, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(aiChatService.chat(request.getMessage(), currentUser));
    }

    @Override
    public ResponseEntity<AiChatHistoryResponse> history(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(aiChatService.getConversation(currentUser));
    }

    @Override
    public ResponseEntity<Void> reset(@AuthenticationPrincipal User currentUser) {
        aiChatService.resetConversation(currentUser);
        return ResponseEntity.noContent().build();
    }
}
