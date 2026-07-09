package com.timekeeper.bibexpo.messaging.campaign.repository;

import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SmsTemplateRepository extends JpaRepository<SmsTemplate, Long>, JpaSpecificationExecutor<SmsTemplate> {

    /**
     * Find SMS template by ID and Event ID for event-scoped lookup
     */
    Optional<SmsTemplate> findByIdAndEventId(Long id, Long eventId);

    /**
     * Find SMS template by user-provided template ID and Event ID
     */
    Optional<SmsTemplate> findBySmsTemplateIdAndEventId(String smsTemplateId, Long eventId);

    /**
     * Check if SMS template ID already exists for the event (for create validation)
     */
    boolean existsBySmsTemplateIdAndEventId(String smsTemplateId, Long eventId);

    /**
     * Check if SMS template ID already exists for the event excluding current ID (for update validation)
     */
    boolean existsBySmsTemplateIdAndEventIdAndIdNot(String smsTemplateId, Long eventId, Long id);

    int countByEventId(Long eventId);
}
