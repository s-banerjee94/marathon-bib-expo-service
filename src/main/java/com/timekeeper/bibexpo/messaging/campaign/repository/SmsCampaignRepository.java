package com.timekeeper.bibexpo.messaging.campaign.repository;

import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SmsCampaignRepository extends CampaignBaseRepository<SmsCampaign> {

    /**
     * Find campaign by ID with event and smsTemplate eagerly loaded (avoids LazyInitializationException in async context)
     */
    @Query("SELECT c FROM SmsCampaign c JOIN FETCH c.event JOIN FETCH c.smsTemplate WHERE c.id = :id")
    Optional<SmsCampaign> findByIdWithDetails(@Param("id") Long id);

    /**
     * Check if any campaign references this template — used to block template deletion
     */
    boolean existsBySmsTemplateId(Long smsTemplateId);

    /**
     * Check if a template is referenced by any campaign in one of the given statuses — used to block template edits
     */
    boolean existsBySmsTemplateIdAndStatusIn(Long smsTemplateId, List<CampaignStatus> statuses);
}
