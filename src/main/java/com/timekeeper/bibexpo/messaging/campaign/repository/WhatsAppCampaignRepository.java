package com.timekeeper.bibexpo.messaging.campaign.repository;

import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsAppCampaignRepository extends JpaRepository<WhatsAppCampaign, Long>,
        JpaSpecificationExecutor<WhatsAppCampaign> {

    /**
     * Find campaign by ID and event ID for event-scoped lookup
     */
    Optional<WhatsAppCampaign> findByIdAndEventId(Long id, Long eventId);

    int countByEventId(Long eventId);

    /**
     * Find campaign by ID with its template eagerly loaded
     * (avoids LazyInitializationException in async context)
     */
    @Query("SELECT c FROM WhatsAppCampaign c JOIN FETCH c.whatsAppTemplate WHERE c.id = :id")
    Optional<WhatsAppCampaign> findByIdWithDetails(@Param("id") Long id);

    /**
     * Find the active auto-triggered bib-collected campaign for an event
     */
    Optional<WhatsAppCampaign> findByEventIdAndTriggerTypeAndStatus(Long eventId, CampaignTriggerType triggerType, CampaignStatus status);

    /**
     * Check if an ACTIVE AUTO_BIB_COLLECTED campaign already exists for the event
     */
    boolean existsByEventIdAndTriggerTypeAndStatus(Long eventId, CampaignTriggerType triggerType, CampaignStatus status);

    /**
     * Find SCHEDULED ACTIVE campaigns whose scheduledAt has passed — ready to send
     */
    @Query("SELECT c FROM WhatsAppCampaign c WHERE c.triggerType = :triggerType AND c.status = :status AND c.scheduledAt <= :now")
    List<WhatsAppCampaign> findDueCampaigns(@Param("triggerType") CampaignTriggerType triggerType,
                                            @Param("status") CampaignStatus status,
                                            @Param("now") Instant now);

    /**
     * Find campaigns stuck in SENDING (updatedAt older than threshold) — need recovery or failure
     */
    @Query("SELECT c FROM WhatsAppCampaign c WHERE c.status = :status AND c.updatedAt < :threshold")
    List<WhatsAppCampaign> findStuckCampaigns(@Param("status") CampaignStatus status,
                                              @Param("threshold") Instant threshold);

    /**
     * Find all campaigns by status — used on startup to recover SENDING campaigns
     */
    List<WhatsAppCampaign> findAllByStatus(CampaignStatus status);

    /**
     * Check if any campaign references this template — used to block template deletion
     */
    boolean existsByWhatsAppTemplateId(Long whatsAppTemplateId);
}
