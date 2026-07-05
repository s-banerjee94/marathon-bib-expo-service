package com.timekeeper.bibexpo.passwordreset.service.impl;

import com.timekeeper.bibexpo.exception.InvalidUserDataException;
import com.timekeeper.bibexpo.exception.UserNotFoundException;
import com.timekeeper.bibexpo.messaging.delivery.DeliveryResult;
import com.timekeeper.bibexpo.messaging.delivery.SystemMessageDispatcher;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.model.dto.audit.AuditEvent;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.passwordreset.config.PasswordResetProperties;
import com.timekeeper.bibexpo.passwordreset.exception.PasswordResetInvalidException;
import com.timekeeper.bibexpo.passwordreset.model.PasswordResetMessageContext;
import com.timekeeper.bibexpo.passwordreset.model.PasswordResetToken;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.CompletePasswordResetRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.ForgotPasswordRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.request.IssueResetLinkRequest;
import com.timekeeper.bibexpo.passwordreset.model.dto.response.PasswordResetLinkResponse;
import com.timekeeper.bibexpo.passwordreset.model.dto.response.PasswordResetTokenStatusResponse;
import com.timekeeper.bibexpo.passwordreset.service.PasswordResetService;
import com.timekeeper.bibexpo.passwordreset.store.PasswordResetStore;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.service.UserService;
import com.timekeeper.bibexpo.service.audit.AuditPublisher;
import com.timekeeper.bibexpo.service.cache.AuthUserCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthUserCache authUserCache;
    private final AuditPublisher auditPublisher;
    private final PasswordResetStore passwordResetStore;
    private final PasswordResetProperties passwordResetProperties;
    private final SystemMessageDispatcher systemMessageDispatcher;

    @Override
    @Transactional(readOnly = true)
    public PasswordResetLinkResponse issueForUser(Long userId, IssueResetLinkRequest request, String currentUsername) {
        log.info("Password reset link requested for user ID: {} by: {}", userId, currentUsername);

        userService.assertCanUpdateUser(userId, currentUsername);
        User target = fetchUser(userId);

        // A signed-in user must not mint a reset link for their own account: that would bypass the
        // current-password check on change-password and let a hijacked session take the account over.
        // Self-service goes through change-password (needs the current password) or forgot-password
        // (link delivered to the account's own phone).
        if (target.getUsername().equals(currentUsername)) {
            throw new InvalidUserDataException(
                    "Use change password or forgot password to reset your own account.");
        }

        String resetUrl = issueLink(target, currentUsername);

        Set<MessageChannel> channels = request != null && request.getDeliveryChannels() != null
                ? request.getDeliveryChannels() : Set.of();
        List<DeliveryResult> deliveries = deliverResetLink(target, channels, resetUrl);

        auditLinkIssued(currentUsername, target);
        log.info("Password reset link issued for user ID: {} by: {} — channels: {}", userId, currentUsername, channels);
        return PasswordResetLinkResponse.builder()
                .resetUrl(resetUrl)
                .deliveries(deliveries)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public void requestReset(ForgotPasswordRequest request) {
        Optional<User> match = findByIdentifier(request.getIdentifier());
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

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        passwordResetStore.consume(token);
        authUserCache.evict(user.getUsername());

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

    private Optional<User> findByIdentifier(String identifier) {
        String value = identifier == null ? null : identifier.trim();
        if (isBlank(value)) {
            return Optional.empty();
        }
        return userRepository.findByUsername(value)
                .or(() -> userRepository.findByEmail(value))
                .or(() -> userRepository.findByPhoneNumber(value));
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
        return userRepository.findById(reset.getUserId())
                .orElseThrow(() -> new PasswordResetInvalidException(
                        "This password reset link is invalid or has expired."));
    }

    private User fetchUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private String buildResetUrl(String token) {
        return UriComponentsBuilder.fromUriString(passwordResetProperties.getBaseUrl())
                .path(passwordResetProperties.getResetPath())
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private void auditLinkIssued(String adminUsername, User target) {
        String label = labelOf(target);
        auditPublisher.publish(AuditEvent.builder()
                .organizationId(organizationIdOf(target))
                .actorUserId(userRepository.findByUsername(adminUsername).map(User::getId).orElse(null))
                .actorName(adminUsername)
                .action(AuditAction.PASSWORD_RESET)
                .entityType(AuditEntityType.USER)
                .entityId(target.getId().toString())
                .entityLabel(label)
                .description(adminUsername + " generated a password reset link for " + label)
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
        return (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName() : user.getUsername();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
