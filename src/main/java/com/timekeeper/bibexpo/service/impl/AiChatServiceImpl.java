package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.ai.mcp.McpToolGroup;
import com.timekeeper.bibexpo.ai.prompt.SystemPromptProvider;
import com.timekeeper.bibexpo.model.dto.response.AiChatHistoryResponse;
import com.timekeeper.bibexpo.model.dto.response.AiChatResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.AiChatMessageRepository;
import com.timekeeper.bibexpo.service.AiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class AiChatServiceImpl implements AiChatService {

    private static final int MAX_MEMORY_MESSAGES = 20;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final AiChatMessageRepository chatMessageRepository;
    private final SystemPromptProvider systemPromptProvider;
    private final Object[] toolGroups;

    public AiChatServiceImpl(ChatClient.Builder chatClientBuilder,
                             List<McpToolGroup> toolGroups,
                             ChatMemoryRepository chatMemoryRepository,
                             AiChatMessageRepository chatMessageRepository,
                             SystemPromptProvider systemPromptProvider) {
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(MAX_MEMORY_MESSAGES)
                .build();
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
        return chatMessageRepository.findFirstByConversationIdOrderByPositionDesc(conversationId)
                .filter(stored -> MessageType.ASSISTANT.name().equals(stored.getMessageType()))
                .map(stored -> AiChatResponse.builder()
                        .id(stored.getId())
                        .role(stored.getMessageType())
                        .reply(reply)
                        .createdAt(stored.getCreatedAt())
                        .build())
                .orElseGet(() -> AiChatResponse.builder()
                        .role(MessageType.ASSISTANT.name())
                        .reply(reply)
                        .createdAt(Instant.now())
                        .build());
    }

    @Override
    public AiChatHistoryResponse getConversation(User currentUser) {
        log.info("Loading AI conversation for user {}", currentUser.getUsername());

        List<AiChatHistoryResponse.Turn> turns = chatMessageRepository
                .findByConversationIdOrderByPositionAsc(memoryKey(currentUser)).stream()
                .map(stored -> AiChatHistoryResponse.Turn.builder()
                        .id(stored.getId())
                        .role(stored.getMessageType())
                        .content(stored.getContent())
                        .createdAt(stored.getCreatedAt())
                        .build())
                .toList();

        return AiChatHistoryResponse.builder().messages(turns).build();
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
