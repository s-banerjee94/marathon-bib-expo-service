package com.timekeeper.bibexpo.messaging.system.service;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.messaging.system.model.entity.SystemMessageTemplate;

/**
 * Resolves the message content (body/variables, template and sender ids) for a system message.
 */
public interface SystemMessageTemplateService {

    /**
     * Returns the enabled template for the given purpose and channel.
     *
     * @throws com.timekeeper.bibexpo.messaging.system.exception.SystemTemplateNotFoundException
     *         if no template exists for the pair or it is disabled
     */
    SystemMessageTemplate resolve(SystemTemplatePurpose purpose, MessageChannel channel);
}
