package com.timekeeper.bibexpo.whatsapp.repository;

import com.timekeeper.bibexpo.whatsapp.model.entity.WhatsAppTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WhatsAppTemplateRepository extends JpaRepository<WhatsAppTemplate, Long>,
        JpaSpecificationExecutor<WhatsAppTemplate> {

    long countByEventId(Long eventId);

    boolean existsByContentSidAndEventId(String contentSid, Long eventId);

    boolean existsByContentSidAndEventIdAndIdNot(String contentSid, Long eventId, Long id);

    Optional<WhatsAppTemplate> findByIdAndEventId(Long id, Long eventId);
}
