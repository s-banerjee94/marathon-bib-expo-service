package com.timekeeper.bibexpo.messaging.system.service.impl;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.messaging.system.exception.SystemTemplateNotFoundException;
import com.timekeeper.bibexpo.messaging.system.model.entity.SystemMessageTemplate;
import com.timekeeper.bibexpo.messaging.system.repository.SystemMessageTemplateRepository;
import com.timekeeper.bibexpo.messaging.system.service.SystemMessageTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemMessageTemplateServiceImpl implements SystemMessageTemplateService {

    private final SystemMessageTemplateRepository templateRepository;

    @Override
    @Transactional(readOnly = true)
    public SystemMessageTemplate resolve(SystemTemplatePurpose purpose, MessageChannel channel) {
        SystemMessageTemplate template = templateRepository.findByPurposeAndChannel(purpose, channel)
                .orElseThrow(() -> new SystemTemplateNotFoundException(
                        "No " + channel + " template is configured for " + purpose + "."));
        if (!template.isEnabled()) {
            throw new SystemTemplateNotFoundException(
                    "The " + channel + " template for " + purpose + " is disabled.");
        }
        return template;
    }
}
