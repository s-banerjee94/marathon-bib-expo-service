package com.timekeeper.bibexpo.messaging.campaign.repository;

import com.timekeeper.bibexpo.messaging.campaign.model.entity.CampaignEntity;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Shared finder contract for campaign repositories across channels. Channel repositories
 * extend it with their template-reference checks and eager-fetch queries; the shared campaign
 * service base class works against this interface only.
 *
 * @param <C> concrete campaign entity of the channel
 */
@NoRepositoryBean
public interface CampaignBaseRepository<C extends CampaignEntity>
        extends JpaRepository<C, Long>, JpaSpecificationExecutor<C> {

    /**
     * Find campaign by ID and event ID for event-scoped lookup
     */
    Optional<C> findByIdAndEventId(Long id, Long eventId);

    int countByEventId(Long eventId);

    /**
     * All campaigns of an event.
     */
    List<C> findAllByEventId(Long eventId);

    /**
     * Find the active auto-triggered bib-collected campaign for an event
     */
    Optional<C> findByEventIdAndTriggerTypeAndStatus(Long eventId, CampaignTriggerType triggerType, CampaignStatus status);

    /**
     * Check if an ACTIVE AUTO_BIB_COLLECTED campaign already exists for the event
     */
    boolean existsByEventIdAndTriggerTypeAndStatus(Long eventId, CampaignTriggerType triggerType, CampaignStatus status);

    /**
     * Find SCHEDULED ACTIVE campaigns whose scheduledAt has passed — ready to send
     */
    @Query("SELECT c FROM #{#entityName} c WHERE c.triggerType = :triggerType AND c.status = :status AND c.scheduledAt <= :now")
    List<C> findDueCampaigns(@Param("triggerType") CampaignTriggerType triggerType,
                             @Param("status") CampaignStatus status,
                             @Param("now") Instant now);

    /**
     * Find campaigns stuck in SENDING (updatedAt older than threshold) — need recovery or failure
     */
    @Query("SELECT c FROM #{#entityName} c WHERE c.status = :status AND c.updatedAt < :threshold")
    List<C> findStuckCampaigns(@Param("status") CampaignStatus status,
                               @Param("threshold") Instant threshold);

    /**
     * Find all campaigns by status — used on startup to recover SENDING campaigns
     */
    List<C> findAllByStatus(CampaignStatus status);
}
