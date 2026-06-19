package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, String> {

    List<AiChatMessage> findByConversationIdOrderByPositionAsc(String conversationId);

    Optional<AiChatMessage> findFirstByConversationIdOrderByPositionDesc(String conversationId);

    @Transactional
    void deleteByConversationId(String conversationId);

    @Query("select distinct m.conversationId from AiChatMessage m")
    List<String> findDistinctConversationIds();
}
