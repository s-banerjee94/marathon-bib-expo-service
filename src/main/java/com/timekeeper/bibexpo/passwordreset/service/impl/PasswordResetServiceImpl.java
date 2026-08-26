package com.timekeeper.bibexpo.passwordreset.service.impl;

import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.audit.api.AuditEvent;
import com.timekeeper.bibexpo.audit.api.AuditPublisher;
import com.timekeeper.bibexpo.messaging.delivery.DeliveryResult;
import com.timekeeper.bibexpo.messaging.delivery.SystemMessageDispatcher;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.passwordreset.config.PasswordResetProperties;
import com.timekeeper.bibexpo.passwordreset.exception.PasswordResetInvalidException;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.CompletePasswordResetRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.ForgotPasswordRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.IssueResetLinkRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.response.PasswordResetLinkResponse;
import com.timekeeper.bibexpo.passwordreset.model.dto.response.PasswordResetTokenStatusResponse;
import com.timekeeper.bibexpo.passwordreset.model.PasswordResetMessageContext;
import com.timekeeper.bibexpo.passwordreset.model.PasswordResetToken;
import com.timekeeper.bibexpo.passwordreset.service.PasswordResetService;
import com.timekeeper.bibexpo.passwordreset.store.PasswordResetStore;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.user.api.CurrentActor;
import com.timekeeper.bibexpo.user.api.UserDirectory;
import com.timekeeper.bibexpo.user.model.entity.User;
import com.timekeeper.bibexpo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    /** Channels a forgot-password link is delivered on to the account's own phone (best-effort). */
    private static final Set<MessageChannel> FORGOT_PASSWORD_CHANNELS =
            Set.of(MessageChannel.WHATSAPP, MessageChannel.SMS);

    private final UserDirectory userDirectory;
    private final UserService userService;
    private final AuditPublisher auditPublisher;
    private final PasswordResetStore passwordResetStore;
    private final PasswordResetProperties passwordResetProperties;
    private final SystemMessageDispatcher systemMessageDispatcher;

    @Override
    @Transactional(readOnly = true)
    public PasswordResetLinkResponse issueForUser(Long userId, IssueResetLinkRequest request, CurrentActor actor) {
        log.info("Password reset link requested for user ID: {} by: {}", userId, actor.username());

        userService.assertCanUpdateUser(userId, actor);
        User target = userDirectory.requireById(userId);

        // A signed-in user must not mint a reset link for their own account: that would bypass the
        // current-password check on change-password and let a hijacked session take the account over.
        // Self-service goes through change-password (needs the current password) or forgot-password
        // (link delivered to the account's own phone).
        if (target.getId().equals(actor.id())) {
            throw new InvalidUserDataException(
                    "Use change password or forgot password to reset your own account.");
        }

        String resetUrl = issueLink(target, actor.username());

        Set<MessageChannel> channels = request != null && request.getDeliveryChannels() != null
                ? request.getDeliveryChannels() : Set.of();
        List<DeliveryResult> deliveries = deliverResetLink(target, channels, resetUrl);

        auditLinkIssued(actor, target);
        log.info("Password reset link issued for user ID: {} by: {} — channels: {}", userId, actor.username(), channels);
        return PasswordResetLinkResponse.builder()
                .resetUrl(resetUrl)
                .deliveries(deliveries)
                .build();
    }

    @Override
    @Async("passwordResetTaskExecutor")
    public void requestReset(ForgotPasswordRequest request) {
        // Runs off the request thread so the endpoint responds in constant time whether or not an
        // account matched — otherwise the extra work of sending a link would leak, by response time,
        // that an account exists.
        Optional<User> match = userDirectory.findByLoginIdentifier(request.getIdentifier());
        if (match.isEmpty()) {
            log.info("Forgot-password request did not match any account");
            return;
        }

        User user = match.get();
        // A disabled or locked account is a deliberate administrative hold: never facilitate a
        // self-service password change for it (login enforces the same status regardless).
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            log.info("Forgot-password request for inactive (disabled/locked) account ID: {} — ignored", user.getId());
            return;
        }
        if (isBlank(user.getPhoneNumber())) {
            log.info("Forgot-password request for account ID: {} with no phone on file — cannot deliver", user.getId());
            return;
        }

        String resetUrl = issueLink(user, null);
        deliverResetLink(user, FORGOT_PASSWORD_CHANNELS, resetUrl);
        log.info("Forgot-password link issued and delivered for account ID: {}", user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PasswordResetTokenStatusResponse validate(String token) {
        PasswordResetToken reset = peekOrThrow(token);
        User user = resolveUser(reset);
        return PasswordResetTokenStatusResponse.builder()
                .username(user.getUsername())
                .fullName(user.getFullName())
                .expiresAt(reset.getExpiresAt())
                .build();
    }

    @Override
    @Transactional
    public void completeReset(String token, CompletePasswordResetRequest request) {
        PasswordResetToken reset = peekOrThrow(token);
        User user = resolveUser(reset);

        userService.applyNewPassword(user.getId(), request.getNewPassword());
        passwordResetStore.consume(token);

        auditResetCompleted(user, reset.getIssuedBy());
        log.info("Password reset completed for user ID: {} (issuedBy {})", user.getId(), reset.getIssuedBy());
    }

    /** Issue a token for the user and build the link the user opens to set a new password. */
    private String issueLink(User user, String issuedBy) {
        Instant expiresAt = Instant.now().plus(passwordResetProperties.getTtlMinutes(), ChronoUnit.MINUTES);
        String token = passwordResetStore.issue(PasswordResetToken.builder()
                .userId(user.getId())
                .issuedBy(issuedBy)
                .expiresAt(expiresAt)
                .build());
        return buildResetUrl(token);
    }

    private List<DeliveryResult> deliverResetLink(User user, Set<MessageChannel> channels, String resetUrl) {
        PasswordResetMessageContext context = PasswordResetMessageContext.builder()
                .userName(labelOf(user))
                .resetUrl(resetUrl)
                .build();
        return systemMessageDispatcher.deliver(
                SystemTemplatePurpose.PASSWORD_RESET, channels, user.getPhoneNumber(), context);
    }

    private PasswordResetToken peekOrThrow(String token) {
        PasswordResetToken reset = passwordResetStore.peek(token);
        if (reset == null) {
            throw new PasswordResetInvalidException("This password reset link is invalid or has expired.");
        }
        return reset;
    }

    /** The token's user, or a rejection if it was removed since the link was issued. */
    private User resolveUser(PasswordResetToken reset) {
        return userDirectory.findById(reset.getUserId())
                .orElseThrow(() -> new PasswordResetInvalidException(
                        "This password reset link is invalid or has expired."));
    }

    private String buildResetUrl(String token) {
        return UriComponentsBuilder.fromUriString(passwordResetProperties.getBaseUrl())
                .path(passwordResetProperties.getResetPath())
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private void auditLinkIssued(CurrentActor actor, User target) {
        String label = labelOf(target);
        auditPublisher.publish(AuditEvent.builder()
                .organizationId(organizationIdOf(target))
                .actorUserId(actor.id())
                .actorName(actor.username())
                .action(AuditAction.PASSWORD_RESET)
                .entityType(AuditEntityType.USER)
                .entityId(target.getId().toString())
                .entityLabel(label)
                .description(actor.username() + " generated a password reset link for " + label)
                .occurredAt(Instant.now())
                .build());
    }

    private void auditResetCompleted(User user, String issuedBy) {
        String label = labelOf(user);
        String origin = issuedBy == null ? "via forgot-password" : "via admin link";
        auditPublisher.publish(AuditEvent.builder()
                .organizationId(organizationIdOf(user))
                .actorUserId(user.getId())
                .actorName(user.getUsername())
                .action(AuditAction.PASSWORD_RESET)
                .entityType(AuditEntityType.USER)
                .entityId(user.getId().toString())
                .entityLabel(label)
                .description(label + " reset their password (" + origin + ")")
                .occurredAt(Instant.now())
                .build());
    }

    private Long organizationIdOf(User user) {
        return user.getOrganization() != null ? user.getOrganization().getId() : 0L;
    }

    private String labelOf(User user) {
        return !isBlank(user.getFullName())
                ? user.getFullName() : user.getUsername();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
