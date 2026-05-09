package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.model.enums.SmsCampaignStatus;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SmsCampaignRepository extends JpaRepository<SmsCampaign, Long>, JpaSpecificationExecutor<SmsCampaign> {

    /**
     * Find campaign by ID and event ID for event-scoped lookup
     */
    Optional<SmsCampaign> findByIdAndEventId(Long id, Long eventId);

    /**
     * Find the active auto-triggered bib-collected campaign for an event
     */
    Optional<SmsCampaign> findByEventIdAndTriggerTypeAndStatus(Long eventId, SmsCampaignTriggerType triggerType, SmsCampaignStatus status);

    /**
     * Check if an active AUTO_BIB_COLLECTED campaign already exists for the event (DRAFT or ACTIVE)
     */
    boolean existsByEventIdAndTriggerTypeAndStatusIn(Long eventId, SmsCampaignTriggerType triggerType, List<SmsCampaignStatus> statuses);
}
