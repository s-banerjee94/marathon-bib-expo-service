package com.timekeeper.bibexpo.messaging.provider.repository;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Access to the per-channel provider configuration rows.
 */
public interface MessagingProviderRepository extends JpaRepository<MessagingProvider, Long> {

    Optional<MessagingProvider> findByChannel(MessageChannel channel);
}
