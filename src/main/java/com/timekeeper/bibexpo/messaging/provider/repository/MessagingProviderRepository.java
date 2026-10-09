package com.timekeeper.bibexpo.messaging.provider.repository;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageUsage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Access to provider configuration rows, scoped by usage, channel, and organization.
 */
public interface MessagingProviderRepository extends JpaRepository<MessagingProvider, Long> {

    /** A specific organization's override for a channel. */
    Optional<MessagingProvider> findByUsageAndChannelAndOrganizationId(
            MessageUsage usage, MessageChannel channel, Long organizationId);

    /** The platform-default row for a channel (no organization). */
    Optional<MessagingProvider> findByUsageAndChannelAndOrganizationIdIsNull(
            MessageUsage usage, MessageChannel channel);

    /** Every row of one usage, across channels and organizations. */
    List<MessagingProvider> findByUsage(MessageUsage usage);

    /** An organization's campaign overrides across channels. */
    List<MessagingProvider> findByUsageAndOrganizationId(MessageUsage usage, Long organizationId);

    /** The platform-default rows across channels. */
    List<MessagingProvider> findByUsageAndOrganizationIdIsNull(MessageUsage usage);
}
