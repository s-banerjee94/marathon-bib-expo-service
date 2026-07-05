package com.timekeeper.bibexpo.invitation.service.impl;

import com.timekeeper.bibexpo.invitation.config.InviteProperties;
import com.timekeeper.bibexpo.invitation.exception.InvitationInvalidException;
import com.timekeeper.bibexpo.invitation.model.Invitation;
import com.timekeeper.bibexpo.invitation.model.InviteMessageContext;
import com.timekeeper.bibexpo.invitation.model.dto.request.AcceptInvitationRequest;
import com.timekeeper.bibexpo.invitation.model.dto.request.CreateInvitationRequest;
import com.timekeeper.bibexpo.invitation.model.dto.response.InvitationDetailsResponse;
import com.timekeeper.bibexpo.invitation.model.dto.response.InvitationLinkResponse;
import com.timekeeper.bibexpo.invitation.store.InvitationStore;
import com.timekeeper.bibexpo.messaging.delivery.DeliveryResult;
import com.timekeeper.bibexpo.messaging.delivery.SystemMessageDispatcher;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.exception.InvalidUserDataException;
import com.timekeeper.bibexpo.model.dto.request.CreateUserRequest;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.service.UserService;
import com.timekeeper.bibexpo.util.SmsTemplateParser;
import com.timekeeper.bibexpo.invitation.service.InvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationServiceImpl implements InvitationService {

    private final InvitationStore invitationStore;
    private final UserService userService;
    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final InviteProperties inviteProperties;
    private final SystemMessageDispatcher systemMessageDispatcher;

    @Override
    public InvitationLinkResponse createInvitation(CreateInvitationRequest request, String currentUsername) {
        UserRole role = UserRole.valueOf(request.getRole());
        userService.assertCanCreateUser(role, request.getOrganizationId(), request.getEventId(), currentUsername);

        Set<MessageChannel> channels = request.getDeliveryChannels() == null ? Set.of() : request.getDeliveryChannels();
        if (requiresPhone(channels) && isBlank(request.getRecipientPhone())) {
            throw new InvalidUserDataException("A phone number is required to send the invite.");
        }

        Instant expiresAt = Instant.now().plus(inviteProperties.getTtlMinutes(), ChronoUnit.MINUTES);
        String token = invitationStore.issue(Invitation.builder()
                .role(role)
                .organizationId(request.getOrganizationId())
                .eventId(request.getEventId())
                .invitedBy(currentUsername)
                .recipientPhone(request.getRecipientPhone())
                .expiresAt(expiresAt)
                .build());
        String inviteUrl = buildInviteUrl(token);

        List<DeliveryResult> deliveries = systemMessageDispatcher.deliver(
                SystemTemplatePurpose.INVITE, channels, request.getRecipientPhone(),
                buildContext(role, request.getOrganizationId(), inviteUrl));

        log.info("Invite issued for role {} (org {}) by {} — channels: {}",
                role, request.getOrganizationId(), currentUsername, channels);
        return InvitationLinkResponse.builder()
                .inviteUrl(inviteUrl)
                .deliveries(deliveries)
                .build();
    }

    @Override
    public InvitationDetailsResponse getInvitation(String token) {
        Invitation invitation = invitationStore.peek(token);
        if (invitation == null) {
            throw new InvitationInvalidException("This invitation link is invalid or has expired.");
        }

        return InvitationDetailsResponse.builder()
                .role(invitation.getRole().name())
                .organizationId(invitation.getOrganizationId())
                .organizationName(resolveOrganizationName(invitation.getOrganizationId()))
                .eventId(invitation.getEventId())
                .eventName(resolveEventName(invitation.getEventId()))
                .recipientPhone(invitation.getRecipientPhone())
                .expiresAt(invitation.getExpiresAt())
                .build();
    }

    @Override
    public UserResponse acceptInvitation(String token, AcceptInvitationRequest request) {
        Invitation invitation = invitationStore.peek(token);
        if (invitation == null) {
            throw new InvitationInvalidException("This invitation link is invalid or has expired.");
        }

        // Create first; only consume the token once creation succeeds, so a recoverable
        // failure (such as a username already taken) leaves the link usable for a retry.
        UserResponse response = userService.createInvitedUser(toCreateUserRequest(request, invitation));
        invitationStore.consume(token);

        log.info("Invite accepted, created user {} with role {}", response.getUsername(), response.getRole());
        return response;
    }

    private CreateUserRequest toCreateUserRequest(AcceptInvitationRequest request, Invitation invitation) {
        return CreateUserRequest.builder()
                .username(request.getUsername())
                .password(request.getPassword())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .role(invitation.getRole().name())
                .organizationId(invitation.getOrganizationId())
                .eventId(invitation.getEventId())
                .build();
    }

    private boolean requiresPhone(Set<MessageChannel> channels) {
        return channels.contains(MessageChannel.WHATSAPP) || channels.contains(MessageChannel.SMS);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private InviteMessageContext buildContext(UserRole role, Long organizationId, String inviteUrl) {
        return InviteMessageContext.builder()
                .role(humanizeRole(role))
                .organizationName(resolveOrganizationName(organizationId))
                .inviteUrl(inviteUrl)
                .build();
    }

    /** "ORGANIZER_ADMIN" -> "Organizer Admin", for human-readable message text. */
    private String humanizeRole(UserRole role) {
        return Arrays.stream(role.name().toLowerCase().split("_"))
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String resolveOrganizationName(Long organizationId) {
        if (organizationId == null) {
            return null;
        }
        return organizationRepository.findById(organizationId)
                .map(Organization::getOrganizerName)
                .orElse(null);
    }

    private String resolveEventName(Long eventId) {
        if (eventId == null) {
            return null;
        }
        return eventRepository.findById(eventId)
                .map(Event::getEventName)
                .orElse(null);
    }

    private String buildInviteUrl(String token) {
        return UriComponentsBuilder.fromUriString(inviteProperties.getBaseUrl())
                .path(inviteProperties.getAcceptPath())
                .queryParam("token", token)
                .build()
                .toUriString();
    }
}
