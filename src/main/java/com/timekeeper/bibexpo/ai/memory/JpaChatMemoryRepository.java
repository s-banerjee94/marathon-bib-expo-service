package com.timekeeper.bibexpo.ai.memory;

import com.timekeeper.bibexpo.model.entity.AiChatMessage;
import com.timekeeper.bibexpo.repository.AiChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Persists per-conversation chat history in MySQL so conversations survive restarts and scale
 * beyond memory. Only user and assistant text turns are stored — a clean transcript for context;
 * transient tool/system messages are skipped.
 */
@Component
@Primary
@RequiredArgsConstructor
public class JpaChatMemoryRepository implements ChatMemoryRepository {

    private final AiChatMessageRepository repository;

    @Override
    public List<String> findConversationIds() {
        return repository.findDistinctConversationIds();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> findByConversationId(String conversationId) {
        return repository.findByConversationIdOrderByPositionAsc(conversationId).stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        repository.deleteByConversationId(conversationId);
        List<Message> persistable = messages.stream()
                .filter(this::isPersistable)
                .toList();
        List<AiChatMessage> entities = IntStream.range(0, persistable.size())
                .mapToObj(i -> toEntity(conversationId, persistable.get(i), i))
                .toList();
        if (!entities.isEmpty()) {
            repository.saveAll(entities);
        }
    }

    @Override
    @Transactional
    public void deleteByConversationId(String conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    private boolean isPersistable(Message message) {
        MessageType type = message.getMessageType();
        return (type == MessageType.USER || type == MessageType.ASSISTANT)
                && message.getText() != null && !message.getText().isBlank();
    }

    private AiChatMessage toEntity(String conversationId, Message message, int position) {
        return AiChatMessage.builder()
                .conversationId(conversationId)
                .messageType(message.getMessageType().name())
                .content(message.getText())
                .position(position)
                .createdAt(Instant.now())
                .build();
    }

    private Message toMessage(AiChatMessage entity) {
        if (MessageType.ASSISTANT.name().equals(entity.getMessageType())) {
            return new AssistantMessage(entity.getContent());
        }
        return new UserMessage(entity.getContent());
    }
}
