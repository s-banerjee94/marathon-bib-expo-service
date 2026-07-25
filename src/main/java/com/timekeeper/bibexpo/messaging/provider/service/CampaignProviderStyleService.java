package com.timekeeper.bibexpo.messaging.provider.service;

import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.messaging.provider.model.dto.response.CampaignProviderStyleResponse;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers "which campaign provider will send for this event's organization, and how does it want the
 * message rendered?" for the campaign template editor. Resolves the same override-else-default row the
 * send path uses and returns only its rendering hints — never the connection or secrets.
 */
@Service
@RequiredArgsConstructor
public class CampaignProviderStyleService {

    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;
    private final CampaignProviderResolver campaignProviderResolver;

    @Transactional(readOnly = true)
    public CampaignProviderStyleResponse getStyle(Long eventId, MessageChannel channel, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);
        eventAccessValidator.validateUserOrganizationAccess(currentUser, event);

        Long organizationId = event.getOrganization() != null ? event.getOrganization().getId() : null;

        return campaignProviderResolver.resolveOptional(channel, organizationId)
                .map(resolved -> CampaignProviderStyleResponse.builder()
                        .hasProvider(true)
                        .source(resolved.source())
                        .templateMode(resolved.provider().getTemplateMode())
                        .contentType(resolved.provider().getContentType())
                        .build())
                .orElseGet(() -> CampaignProviderStyleResponse.builder().hasProvider(false).build());
    }
}
