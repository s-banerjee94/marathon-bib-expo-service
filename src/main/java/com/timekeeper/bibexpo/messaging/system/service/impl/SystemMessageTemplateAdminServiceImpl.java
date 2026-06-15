package com.timekeeper.bibexpo.messaging.system.service.impl;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.messaging.shared.exception.MessagingConfigNotFoundException;
import com.timekeeper.bibexpo.messaging.system.model.dto.request.SaveSystemMessageTemplateRequest;
import com.timekeeper.bibexpo.messaging.system.model.dto.response.SystemMessageTemplateResponse;
import com.timekeeper.bibexpo.messaging.system.model.entity.SystemMessageTemplate;
import com.timekeeper.bibexpo.messaging.system.repository.SystemMessageTemplateRepository;
import com.timekeeper.bibexpo.messaging.system.service.SystemMessageTemplateAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemMessageTemplateAdminServiceImpl implements SystemMessageTemplateAdminService {

    private final SystemMessageTemplateRepository templateRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SystemMessageTemplateResponse> list() {
        return templateRepository.findAll().stream()
                .sorted(Comparator.comparing(SystemMessageTemplate::getPurpose)
                        .thenComparing(SystemMessageTemplate::getChannel))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SystemMessageTemplateResponse get(SystemTemplatePurpose purpose, MessageChannel channel) {
        return toResponse(findOrThrow(purpose, channel));
    }

    @Override
    @Transactional
    public SystemMessageTemplateResponse save(SystemTemplatePurpose purpose, MessageChannel channel,
                                              SaveSystemMessageTemplateRequest request) {
        SystemMessageTemplate template = templateRepository.findByPurposeAndChannel(purpose, channel)
                .orElseGet(() -> SystemMessageTemplate.builder().purpose(purpose).channel(channel).build());

        template.setBody(request.getBody());
        template.setVariables(request.getVariables());
        template.setDltTemplateId(request.getDltTemplateId());
        template.setSenderId(request.getSenderId());
        template.setEnabled(request.isEnabled());

        return toResponse(templateRepository.save(template));
    }

    private SystemMessageTemplate findOrThrow(SystemTemplatePurpose purpose, MessageChannel channel) {
        return templateRepository.findByPurposeAndChannel(purpose, channel)
                .orElseThrow(() -> new MessagingConfigNotFoundException(
                        "No " + channel + " template is configured for " + purpose + "."));
    }

    private SystemMessageTemplateResponse toResponse(SystemMessageTemplate template) {
        return SystemMessageTemplateResponse.builder()
                .purpose(template.getPurpose())
                .channel(template.getChannel())
                .body(template.getBody())
                .variables(template.getVariables())
                .dltTemplateId(template.getDltTemplateId())
                .senderId(template.getSenderId())
                .enabled(template.isEnabled())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
