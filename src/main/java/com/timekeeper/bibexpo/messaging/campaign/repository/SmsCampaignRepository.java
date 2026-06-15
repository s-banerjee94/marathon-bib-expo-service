package com.timekeeper.bibexpo.messaging.campaign.repository;

import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
    Optional<SmsCampaign> findByEventIdAndTriggerTypeAndStatus(Long eventId, CampaignTriggerType triggerType, CampaignStatus status);

    /**
     * Check if an ACTIVE AUTO_BIB_COLLECTED campaign already exists for the event
     */
    boolean existsByEventIdAndTriggerTypeAndStatus(Long eventId, CampaignTriggerType triggerType, CampaignStatus status);

    /**
     * Find SCHEDULED ACTIVE campaigns whose scheduledAt has passed — ready to send
     */
    @Query("SELECT c FROM SmsCampaign c WHERE c.triggerType = :triggerType AND c.status = :status AND c.scheduledAt <= :now")
    List<SmsCampaign> findDueCampaigns(@Param("triggerType") CampaignTriggerType triggerType,
                                       @Param("status") CampaignStatus status,
                                       @Param("now") Instant now);

    /**
     * Find campaigns stuck in SENDING (updatedAt older than threshold) — need recovery or failure
     */
    @Query("SELECT c FROM SmsCampaign c WHERE c.status = :status AND c.updatedAt < :threshold")
    List<SmsCampaign> findStuckCampaigns(@Param("status") CampaignStatus status,
                                         @Param("threshold") Instant threshold);

    /**
     * Find all campaigns by status — used on startup to recover SENDING campaigns
     */
    List<SmsCampaign> findAllByStatus(CampaignStatus status);

    /**
     * Check if any campaign references this template — used to block template deletion
     */
    boolean existsBySmsTemplateId(Long smsTemplateId);

    /**
     * Check if a template is referenced by any campaign in one of the given statuses — used to block template edits
     */
    boolean existsBySmsTemplateIdAndStatusIn(Long smsTemplateId, List<CampaignStatus> statuses);
}
