package com.timekeeper.bibexpo.messaging.campaign.repository;

import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsAppCampaignRepository extends CampaignBaseRepository<WhatsAppCampaign> {

    /**
     * Find campaign by ID with its template eagerly loaded
     * (avoids LazyInitializationException in async context)
     */
    @Query("SELECT c FROM WhatsAppCampaign c JOIN FETCH c.whatsAppTemplate WHERE c.id = :id")
    Optional<WhatsAppCampaign> findByIdWithDetails(@Param("id") Long id);

    /**
     * Check if any campaign references this template — used to block template deletion
     */
    boolean existsByWhatsAppTemplateId(Long whatsAppTemplateId);

    /**
     * Check if a template is referenced by any campaign in one of the given statuses — used to block template edits
     */
    boolean existsByWhatsAppTemplateIdAndStatusIn(Long whatsAppTemplateId, List<CampaignStatus> statuses);
}
