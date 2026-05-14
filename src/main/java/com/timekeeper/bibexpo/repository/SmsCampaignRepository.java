package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.model.enums.SmsCampaignStatus;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SmsCampaignRepository extends JpaRepository<SmsCampaign, Long>, JpaSpecificationExecutor<SmsCampaign> {

    /**
     * Find campaign by ID and event ID for event-scoped lookup
     */
    Optional<SmsCampaign> findByIdAndEventId(Long id, Long eventId);

    int countByEventId(Long eventId);

    /**
     * Find campaign by ID with event and smsTemplate eagerly loaded (avoids LazyInitializationException in async context)
     */
    @Query("SELECT c FROM SmsCampaign c JOIN FETCH c.event JOIN FETCH c.smsTemplate WHERE c.id = :id")
    Optional<SmsCampaign> findByIdWithDetails(@Param("id") Long id);

    /**
     * Find the active auto-triggered bib-collected campaign for an event
     */
    Optional<SmsCampaign> findByEventIdAndTriggerTypeAndStatus(Long eventId, SmsCampaignTriggerType triggerType, SmsCampaignStatus status);

    /**
     * Check if an ACTIVE AUTO_BIB_COLLECTED campaign already exists for the event
     */
    boolean existsByEventIdAndTriggerTypeAndStatus(Long eventId, SmsCampaignTriggerType triggerType, SmsCampaignStatus status);

    /**
     * Find SCHEDULED ACTIVE campaigns whose scheduledAt has passed — ready to send
     */
    @Query("SELECT c FROM SmsCampaign c WHERE c.triggerType = :triggerType AND c.status = :status AND c.scheduledAt <= :now")
    List<SmsCampaign> findDueCampaigns(@Param("triggerType") SmsCampaignTriggerType triggerType,
                                       @Param("status") SmsCampaignStatus status,
                                       @Param("now") LocalDateTime now);

    /**
     * Find campaigns stuck in SENDING (updatedAt older than threshold) — need recovery or failure
     */
    @Query("SELECT c FROM SmsCampaign c WHERE c.status = :status AND c.updatedAt < :threshold")
    List<SmsCampaign> findStuckCampaigns(@Param("status") SmsCampaignStatus status,
                                         @Param("threshold") LocalDateTime threshold);

    /**
     * Find all campaigns by status — used on startup to recover SENDING campaigns
     */
    List<SmsCampaign> findAllByStatus(SmsCampaignStatus status);

    /**
     * Check if any campaign references this template — used to block template deletion
     */
    boolean existsBySmsTemplateId(Long smsTemplateId);
}
