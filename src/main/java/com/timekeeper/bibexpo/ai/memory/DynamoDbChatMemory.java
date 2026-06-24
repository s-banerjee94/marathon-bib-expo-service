package com.timekeeper.bibexpo.ai.memory;

import com.timekeeper.bibexpo.model.dynamodb.AiChatMessageDDB;
import com.timekeeper.bibexpo.repository.dynamodb.AiChatMessageDDBRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Chat memory backed by DynamoDB ({@code marathon-ai-chat-memory}). The full conversation is kept
 * append-only — each new turn is added at the next position and never rewritten — so the history can
 * be paged back through, while {@link #get} returns only the most recent turns as the model's context
 * window. Conversation id is the partition key and the ever-increasing position the sort key; idle
 * conversations auto-expire via the table's TTL. Only user and assistant text turns are stored.
 */
@Component
public class DynamoDbChatMemory implements ChatMemory {

    /** Most recent turns handed to the model as context on each request. */
    private static final int CONTEXT_WINDOW = 20;

    private final AiChatMessageDDBRepository repository;
    private final long ttlSeconds;

    public DynamoDbChatMemory(AiChatMessageDDBRepository repository,
                              @Value("${app.ai.memory.ttl-days:30}") int ttlDays) {
        this.repository = repository;
        this.ttlSeconds = Duration.ofDays(ttlDays).toSeconds();
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> persistable = messages.stream()
                .filter(this::isPersistable)
                .toList();
        if (persistable.isEmpty()) {
            return;
        }

        int next = repository.findMaxPosition(conversationId) + 1;
        long expirationTime = Instant.now().getEpochSecond() + ttlSeconds;
        List<AiChatMessageDDB> items = IntStream.range(0, persistable.size())
                .mapToObj(i -> toItem(conversationId, persistable.get(i), next + i, expirationTime))
                .toList();

        repository.putAll(items);
    }

    @Override
    public List<Message> get(String conversationId) {
        return repository.findLastN(conversationId, CONTEXT_WINDOW).stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    public void clear(String conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    private boolean isPersistable(Message message) {
        MessageType type = message.getMessageType();
        return (type == MessageType.USER || type == MessageType.ASSISTANT)
                && message.getText() != null && !message.getText().isBlank();
    }

    private AiChatMessageDDB toItem(String conversationId, Message message, int position, long expirationTime) {
        return AiChatMessageDDB.builder()
                .conversationId(conversationId)
                .position(position)
                .id(UUID.randomUUID().toString())
                .messageType(message.getMessageType().name())
                .content(message.getText())
                .createdAt(Instant.now().toString())
                .expirationTime(expirationTime)
                .build();
    }

    private Message toMessage(AiChatMessageDDB item) {
        if (MessageType.ASSISTANT.name().equals(item.getMessageType())) {
            return new AssistantMessage(item.getContent());
        }
        return new UserMessage(item.getContent());
    }
}
