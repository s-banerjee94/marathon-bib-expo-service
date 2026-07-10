package com.timekeeper.bibexpo.messaging.campaign.repository;

import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppTemplate;
import org.springframework.stereotype.Repository;

@Repository
public interface WhatsAppTemplateRepository extends TemplateBaseRepository<WhatsAppTemplate> {

    /**
     * Check if Content SID already exists for the event (for create validation)
     */
    boolean existsByContentSidAndEventId(String contentSid, Long eventId);

    /**
     * Check if Content SID already exists for the event excluding current ID (for update validation)
     */
    boolean existsByContentSidAndEventIdAndIdNot(String contentSid, Long eventId, Long id);
}
