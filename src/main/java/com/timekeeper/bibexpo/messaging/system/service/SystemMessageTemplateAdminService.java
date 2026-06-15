package com.timekeeper.bibexpo.messaging.system.service;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.messaging.system.model.dto.request.SaveSystemMessageTemplateRequest;
import com.timekeeper.bibexpo.messaging.system.model.dto.response.SystemMessageTemplateResponse;

import java.util.List;

/**
 * Root management of the system message templates: list, read, and upsert by purpose and channel.
 */
public interface SystemMessageTemplateAdminService {

    List<SystemMessageTemplateResponse> list();

    /**
     * @throws com.timekeeper.bibexpo.messaging.shared.exception.MessagingConfigNotFoundException
     *         if no template exists for the purpose and channel
     */
    SystemMessageTemplateResponse get(SystemTemplatePurpose purpose, MessageChannel channel);

    /**
     * Creates the template for the purpose and channel or replaces its content.
     */
    SystemMessageTemplateResponse save(SystemTemplatePurpose purpose, MessageChannel channel,
                                       SaveSystemMessageTemplateRequest request);
}
