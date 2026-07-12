package com.timekeeper.bibexpo.messaging.campaign.repository;

import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsTemplate;

import java.util.Optional;

public interface SmsTemplateRepository extends TemplateBaseRepository<SmsTemplate> {

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
}
