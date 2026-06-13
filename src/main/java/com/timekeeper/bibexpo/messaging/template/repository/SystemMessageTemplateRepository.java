package com.timekeeper.bibexpo.messaging.template.repository;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.messaging.template.model.entity.SystemMessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Access to the per-purpose, per-channel system message templates.
 */
public interface SystemMessageTemplateRepository extends JpaRepository<SystemMessageTemplate, Long> {

    Optional<SystemMessageTemplate> findByPurposeAndChannel(SystemTemplatePurpose purpose, MessageChannel channel);
}
