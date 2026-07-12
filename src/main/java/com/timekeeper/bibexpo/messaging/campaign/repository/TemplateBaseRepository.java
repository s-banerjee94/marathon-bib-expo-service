package com.timekeeper.bibexpo.messaging.campaign.repository;

import com.timekeeper.bibexpo.messaging.campaign.model.entity.TemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

/**
 * Shared finder contract for message-template repositories across channels. Channel
 * repositories extend it with their channel-identifier uniqueness checks; the shared template
 * service base class works against this interface only.
 *
 * @param <T> concrete template entity of the channel
 */
@NoRepositoryBean
public interface TemplateBaseRepository<T extends TemplateEntity>
        extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {

    /**
     * Find template by ID and Event ID for event-scoped lookup
     */
    Optional<T> findByIdAndEventId(Long id, Long eventId);

    long countByEventId(Long eventId);
}
