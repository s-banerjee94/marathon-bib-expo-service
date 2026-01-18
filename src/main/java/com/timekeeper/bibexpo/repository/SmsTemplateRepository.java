package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.SmsTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
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
     * Find all templates for an event
     */
    List<SmsTemplate> findByEventId(Long eventId);

    /**
     * Find paginated templates for an event
     */
    Page<SmsTemplate> findByEventId(Long eventId, Pageable pageable);

    /**
     * Find only enabled templates for an event
     */
    List<SmsTemplate> findByEventIdAndEnabledTrue(Long eventId);

    /**
     * Find paginated enabled templates for an event
     */
    Page<SmsTemplate> findByEventIdAndEnabledTrue(Long eventId, Pageable pageable);

    /**
     * Check if SMS template ID already exists for the event (for create validation)
     */
    boolean existsBySmsTemplateIdAndEventId(String smsTemplateId, Long eventId);

    /**
     * Check if SMS template ID already exists for the event excluding current ID (for update validation)
     */
    boolean existsBySmsTemplateIdAndEventIdAndIdNot(String smsTemplateId, Long eventId, Long id);

    /**
     * Find templates scheduled between given dates
     */
    List<SmsTemplate> findByScheduledDateTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find scheduled templates that should be sent
     */
    List<SmsTemplate> findByScheduledDateTimeBeforeAndEnabledTrue(LocalDateTime dateTime);

    /**
     * Count templates for an event
     */
    long countByEventId(Long eventId);
}
