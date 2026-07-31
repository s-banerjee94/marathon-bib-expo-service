package com.timekeeper.bibexpo.messaging.direct.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.ParticipantNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.exception.SmsTemplateNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppTemplateNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsTemplate;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppTemplate;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsTemplateRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppTemplateRepository;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignCompatibilityGuard;
import com.timekeeper.bibexpo.messaging.campaign.util.CampaignVariableRenderer;
import com.timekeeper.bibexpo.messaging.provider.service.impl.ProviderMappingValidator.TemplateContent;
import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.direct.exception.InvalidParticipantMessageException;
import com.timekeeper.bibexpo.messaging.direct.model.dto.request.SendParticipantMessagesRequest;
import com.timekeeper.bibexpo.messaging.direct.model.dto.response.ParticipantMessageResult;
import com.timekeeper.bibexpo.messaging.direct.model.dto.response.ParticipantMessagesResponse;
import com.timekeeper.bibexpo.messaging.direct.model.enums.ParticipantMessageStatus;
import com.timekeeper.bibexpo.messaging.direct.service.ParticipantMessageService;
import com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.model.enums.ProviderSource;
import com.timekeeper.bibexpo.messaging.provider.service.CampaignProviderResolver;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateContext;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateParser;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.model.enums.EventOperation;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.dynamodb.ParticipantDDBRepository;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.service.validator.EventOperationGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantMessageServiceImpl implements ParticipantMessageService {

    private static final String NO_PHONE = "This participant does not have a phone number on record.";
    private static final String SEND_FAILED = "The message could not be sent. Please try again.";

    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;
    private final EventOperationGuard eventOperationGuard;
    private final ParticipantDDBRepository participantDDBRepository;
    private final SmsTemplateRepository smsTemplateRepository;
    private final WhatsAppTemplateRepository whatsAppTemplateRepository;
    private final SmsCampaignRepository smsCampaignRepository;
    private final WhatsAppCampaignRepository whatsAppCampaignRepository;
    private final CampaignProviderResolver campaignProviderResolver;
    private final MessagingProviderClient messagingProviderClient;
    private final RaceCategoryNameResolver nameResolver;
    private final CampaignCompatibilityGuard compatibilityGuard;

    /** The resolved template, what it can supply, and how it turns a participant into a payload. */
    private record MessagePlan(Long templateId, String templateName, TemplateContent content, ProviderSource providerSource,
                               Function<MessageTemplateContext, OutboundMessage.OutboundMessageBuilder> payload) {
    }

    @Auditable(entityType = AuditEntityType.PARTICIPANT_MESSAGE, action = AuditAction.SEND)
    @Override
    public ParticipantMessagesResponse sendToParticipants(Long eventId, SendParticipantMessagesRequest request, User currentUser) {
        MessageChannel channel = MessageChannel.valueOf(request.getChannel());
        List<String> bibNumbers = request.getBibNumbers().stream().map(String::trim).distinct().toList();

        log.info("Sending {} to {} participant(s) of event ID: {} by user: {}",
                channel, bibNumbers.size(), eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
        eventOperationGuard.requireAllowed(event, EventOperation.CAMPAIGN_WRITE);

        Long organizationId = event.getOrganization() != null ? event.getOrganization().getId() : null;

        MessagePlan plan = switch (channel) {
            case SMS -> smsPlan(eventId, request.getTemplateId());
            case WHATSAPP -> whatsAppPlan(eventId, request.getTemplateId());
            case EMAIL -> throw new InvalidParticipantMessageException("Email messages are not available yet.");
        };
        // A template the sender cannot fill is a request-level problem, not a per-bib one, so it is
        // refused before a single message is paid for.
        compatibilityGuard.require(channel, organizationId, plan.content(), plan.providerSource(),
                InvalidParticipantMessageException::new);

        MessagingProvider provider = campaignProviderResolver.resolve(channel, organizationId);
        EventNames names = nameResolver.forEvent(eventId);

        List<ParticipantMessageResult> results = bibNumbers.stream()
                .map(bibNumber -> sendOne(bibNumber, event, names, plan, provider, channel))
                .toList();

        int sentCount = (int) results.stream()
                .filter(result -> result.getStatus() == ParticipantMessageStatus.SENT)
                .count();
        int failedCount = results.size() - sentCount;

        AuditContextHolder.setEntityLabel(channel + " to " + String.join(", ", bibNumbers));
        AuditContextHolder.setOrganizationId(organizationId);

        log.info("{} send finished for event ID: {} — {} sent, {} failed", channel, eventId, sentCount, failedCount);

        return ParticipantMessagesResponse.builder()
                .channel(channel)
                .templateId(plan.templateId())
                .templateName(plan.templateName())
                .sentCount(sentCount)
                .failedCount(failedCount)
                .results(results)
                .build();
    }

    private ParticipantMessageResult sendOne(String bibNumber, Event event, EventNames names,
                                             MessagePlan plan, MessagingProvider provider, MessageChannel channel) {
        try {
            ParticipantDDB participant = participantDDBRepository.findByEventAndBibOrThrow(event.getId(), bibNumber);

            String phone = participant.getPhoneNumber();
            if (phone == null || phone.isBlank()) {
                return failed(bibNumber, NO_PHONE);
            }

            MessageTemplateContext context = new MessageTemplateContext(participant, event,
                    names.raceName(participant.getRaceId()), names.categoryName(participant.getCategoryId()),
                    names.reportingTime(participant.getRaceId()));
            messagingProviderClient.send(provider, plan.payload().apply(context).recipientPhone(phone).build());

            log.info("{} sent to bib {} in event {}", channel, bibNumber, event.getId());
            return ParticipantMessageResult.builder()
                    .bibNumber(bibNumber)
                    .status(ParticipantMessageStatus.SENT)
                    .build();

        } catch (ParticipantNotFoundException | MessagingProviderException e) {
            log.warn("{} not sent to bib {} in event {}: {}", channel, bibNumber, event.getId(), e.getMessage());
            return failed(bibNumber, e.getMessage());

        } catch (RuntimeException e) {
            log.error("{} failed unexpectedly for bib {} in event {}", channel, bibNumber, event.getId(), e);
            return failed(bibNumber, SEND_FAILED);
        }
    }

    private ParticipantMessageResult failed(String bibNumber, String reason) {
        return ParticipantMessageResult.builder()
                .bibNumber(bibNumber)
                .status(ParticipantMessageStatus.FAILED)
                .reason(reason)
                .build();
    }

    private MessagePlan smsPlan(Long eventId, Long templateId) {
        SmsTemplate template = templateId != null
                ? smsTemplateRepository.findByIdAndEventId(templateId, eventId).orElseThrow(SmsTemplateNotFoundException::new)
                : autoBibCollectedSmsTemplate(eventId);

        TemplateContent content = new TemplateContent(
                template.getTemplate() != null && !template.getTemplate().isBlank(),
                CampaignVariableRenderer.count(template.getBodyVariables()),
                template.getSmsTemplateId() != null && !template.getSmsTemplateId().isBlank(),
                template.getSenderId() != null && !template.getSenderId().isBlank());

        return new MessagePlan(template.getId(), template.getName(), content, template.getProviderSource(),
                context -> OutboundMessage.builder()
                .templateId(template.getSmsTemplateId())
                .senderId(template.getSenderId())
                .message(MessageTemplateParser.parse(template.getTemplate(), context))
                .variables(CampaignVariableRenderer.render(template.getBodyVariables(), context)));
    }

    private MessagePlan whatsAppPlan(Long eventId, Long templateId) {
        WhatsAppTemplate template = templateId != null
                ? whatsAppTemplateRepository.findByIdAndEventId(templateId, eventId).orElseThrow(WhatsAppTemplateNotFoundException::new)
                : autoBibCollectedWhatsAppTemplate(eventId);

        // The stored body never reaches the wire for WhatsApp — the Content SID carries the text.
        TemplateContent content = new TemplateContent(false,
                CampaignVariableRenderer.count(template.getBodyVariables()),
                template.getContentSid() != null && !template.getContentSid().isBlank(), false);

        return new MessagePlan(template.getId(), template.getName(), content, template.getProviderSource(),
                context -> OutboundMessage.builder()
                .templateId(template.getContentSid())
                .variables(CampaignVariableRenderer.render(template.getBodyVariables(), context)));
    }

    // The template is lazy on the campaign and this runs outside a transaction, so the match is
    // re-read through the eager-fetch query the async senders use.
    private SmsTemplate autoBibCollectedSmsTemplate(Long eventId) {
        return smsCampaignRepository
                .findByEventIdAndTriggerTypeAndStatus(eventId, CampaignTriggerType.AUTO_BIB_COLLECTED, CampaignStatus.ACTIVE)
                .flatMap(campaign -> smsCampaignRepository.findByIdWithDetails(campaign.getId()))
                .map(SmsCampaign::getSmsTemplate)
                .orElseThrow(() -> new InvalidParticipantMessageException(
                        "Choose a template to send — this event has no active bib collection SMS campaign to take one from."));
    }

    private WhatsAppTemplate autoBibCollectedWhatsAppTemplate(Long eventId) {
        return whatsAppCampaignRepository
                .findByEventIdAndTriggerTypeAndStatus(eventId, CampaignTriggerType.AUTO_BIB_COLLECTED, CampaignStatus.ACTIVE)
                .flatMap(campaign -> whatsAppCampaignRepository.findByIdWithDetails(campaign.getId()))
                .map(WhatsAppCampaign::getWhatsAppTemplate)
                .orElseThrow(() -> new InvalidParticipantMessageException(
                        "Choose a template to send — this event has no active bib collection WhatsApp campaign to take one from."));
    }
}
