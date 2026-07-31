package com.timekeeper.bibexpo.messaging.campaign.util;

import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.provider.service.ActiveCampaignCounter;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Counts the campaigns a sender is holding up, for the provider slice's disable and delete guards.
 *
 * <p>ACTIVE campaigns are armed and will fire; SENDING ones are mid-dispatch. DRAFT campaigns are not
 * counted — parking unfinished work must never block an administrator.
 */
@Component
@RequiredArgsConstructor
public class CampaignActiveCounter implements ActiveCampaignCounter {

    private static final List<CampaignStatus> IN_FLIGHT = List.of(CampaignStatus.ACTIVE, CampaignStatus.SENDING);

    private final SmsCampaignRepository smsCampaignRepository;
    private final WhatsAppCampaignRepository whatsAppCampaignRepository;

    @Override
    public int countActive(MessageChannel channel, Long organizationId) {
        return switch (channel) {
            case SMS -> organizationId == null
                    ? smsCampaignRepository.countByStatusIn(IN_FLIGHT)
                    : smsCampaignRepository.countByOrganizationIdAndStatusIn(organizationId, IN_FLIGHT);
            case WHATSAPP -> organizationId == null
                    ? whatsAppCampaignRepository.countByStatusIn(IN_FLIGHT)
                    : whatsAppCampaignRepository.countByOrganizationIdAndStatusIn(organizationId, IN_FLIGHT);
            // No email campaigns exist yet, so an email sender is never holding one up.
            case EMAIL -> 0;
        };
    }
}
