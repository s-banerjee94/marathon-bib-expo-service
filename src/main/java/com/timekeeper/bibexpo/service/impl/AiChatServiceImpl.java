package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.ai.mcp.McpToolGroup;
import com.timekeeper.bibexpo.ai.memory.DynamoDbChatMemory;
import com.timekeeper.bibexpo.ai.prompt.SystemPromptProvider;
import com.timekeeper.bibexpo.model.dto.response.AiChatHistoryResponse;
import com.timekeeper.bibexpo.model.dto.response.AiChatResponse;
import com.timekeeper.bibexpo.model.dynamodb.AiChatMessageDDB;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.dynamodb.AiChatMessageDDBRepository;
import com.timekeeper.bibexpo.service.AiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class AiChatServiceImpl implements AiChatService {

    private static final int DEFAULT_HISTORY_PAGE = 20;
    private static final int MAX_HISTORY_PAGE = 50;

    private final ChatClient chatClient;
    private final DynamoDbChatMemory chatMemory;
    private final AiChatMessageDDBRepository chatMessageRepository;
    private final SystemPromptProvider systemPromptProvider;
    private final Object[] toolGroups;

    public AiChatServiceImpl(ChatClient.Builder chatClientBuilder,
                             List<McpToolGroup> toolGroups,
                             DynamoDbChatMemory chatMemory,
                             AiChatMessageDDBRepository chatMessageRepository,
                             SystemPromptProvider systemPromptProvider) {
        this.chatMemory = chatMemory;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.chatMessageRepository = chatMessageRepository;
        this.systemPromptProvider = systemPromptProvider;
        this.toolGroups = toolGroups.toArray();
    }

    @Override
    public AiChatResponse chat(String message, User currentUser) {
        log.info("AI chat turn for user {}", currentUser.getUsername());
        String conversationId = memoryKey(currentUser);

        String reply = chatClient.prompt()
                .system(systemPromptProvider.get())
                .user(message)
                .tools(toolGroups)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        // The memory advisor has just persisted this turn; the last stored message carries the
        // assistant reply's id and timestamp for the client to render and key on.
        return chatMessageRepository.findLastByConversationId(conversationId)
                .filter(stored -> MessageType.ASSISTANT.name().equals(stored.getMessageType()))
                .map(stored -> AiChatResponse.builder()
                        .id(stored.getId())
                        .role(stored.getMessageType())
                        .reply(reply)
                        .createdAt(Instant.parse(stored.getCreatedAt()))
                        .build())
                .orElseGet(() -> AiChatResponse.builder()
                        .role(MessageType.ASSISTANT.name())
                        .reply(reply)
                        .createdAt(Instant.now())
                        .build());
    }

    @Override
    public AiChatHistoryResponse getConversation(User currentUser, Integer cursor, Integer limit) {
        int pageSize = clampPageSize(limit);
        log.info("Loading AI conversation for user {} - cursor {}, limit {}",
                currentUser.getUsername(), cursor, pageSize);

        // One page of newest-first history; reverse to chronological for rendering.
        List<AiChatMessageDDB> page = chatMessageRepository.findPage(memoryKey(currentUser), cursor, pageSize);

        List<AiChatMessageDDB> chronological = new ArrayList<>(page);
        Collections.reverse(chronological);
        List<AiChatHistoryResponse.Turn> turns = chronological.stream()
                .map(stored -> AiChatHistoryResponse.Turn.builder()
                        .id(stored.getId())
                        .role(stored.getMessageType())
                        .content(stored.getContent())
                        .createdAt(Instant.parse(stored.getCreatedAt()))
                        .build())
                .toList();

        // A full page may have older turns beyond it; the oldest turn's position is the next cursor.
        Integer nextCursor = (page.size() == pageSize && !page.isEmpty())
                ? page.get(page.size() - 1).getPosition()
                : null;

        return AiChatHistoryResponse.builder()
                .messages(turns)
                .nextCursor(nextCursor)
                .build();
    }

    private int clampPageSize(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_HISTORY_PAGE;
        }
        return Math.min(limit, MAX_HISTORY_PAGE);
    }

    @Override
    public void resetConversation(User currentUser) {
        log.info("Resetting AI conversation for user {}", currentUser.getUsername());
        chatMemory.clear(memoryKey(currentUser));
    }

    /** One continuous conversation per user, keyed by user id. */
    private String memoryKey(User currentUser) {
        return "user-" + currentUser.getId();
    }
}
