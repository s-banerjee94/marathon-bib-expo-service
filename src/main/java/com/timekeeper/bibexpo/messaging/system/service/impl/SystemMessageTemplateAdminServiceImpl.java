package com.timekeeper.bibexpo.messaging.system.service.impl;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.messaging.shared.exception.MessagingConfigNotFoundException;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateParser;
import com.timekeeper.bibexpo.messaging.system.exception.InvalidSystemMessageTemplateException;
import com.timekeeper.bibexpo.messaging.system.model.dto.request.SaveSystemMessageTemplateRequest;
import com.timekeeper.bibexpo.messaging.system.model.dto.response.SystemMessageTemplateResponse;
import com.timekeeper.bibexpo.messaging.system.model.entity.SystemMessageTemplate;
import com.timekeeper.bibexpo.messaging.system.repository.SystemMessageTemplateRepository;
import com.timekeeper.bibexpo.messaging.system.service.SystemMessageTemplateAdminService;
import com.timekeeper.bibexpo.messaging.system.service.SystemMessageTemplateCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemMessageTemplateAdminServiceImpl implements SystemMessageTemplateAdminService {

    private final SystemMessageTemplateRepository templateRepository;
    private final SystemMessageTemplateCache templateCache;

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
        validateContent(purpose, request);

        SystemMessageTemplate template = templateRepository.findByPurposeAndChannel(purpose, channel)
                .orElseGet(() -> SystemMessageTemplate.builder().purpose(purpose).channel(channel).build());

        template.setBody(request.getBody());
        template.setVariables(request.getVariables());
        template.setDltTemplateId(request.getDltTemplateId());
        template.setSenderId(request.getSenderId());
        template.setEnabled(request.isEnabled());

        SystemMessageTemplate saved = templateRepository.save(template);
        templateCache.evict(purpose, channel);
        return toResponse(saved);
    }

    /**
     * A placeholder the flow cannot supply renders as an empty string at send time, so a typo would
     * otherwise ship a message with a blank where the link belongs. Names are checked on every save;
     * the link/code itself is only demanded once the template is enabled, so a draft can be parked.
     */
    private void validateContent(SystemTemplatePurpose purpose, SaveSystemMessageTemplateRequest request) {
        rejectUnknownPlaceholders(purpose, request.getBody(), "body");
        rejectUnknownPlaceholders(purpose, request.getVariables(), "variable list");

        if (!request.isEnabled()) {
            return;
        }
        if (isBlank(request.getBody()) && isBlank(request.getVariables())) {
            throw new InvalidSystemMessageTemplateException(
                    "Add a body or variables before enabling this template.");
        }
        requireVariable(purpose, request.getBody(), "body");
        requireVariable(purpose, request.getVariables(), "variable list");
    }

    private void rejectUnknownPlaceholders(SystemTemplatePurpose purpose, String content, String field) {
        List<String> invalid = MessageTemplateParser.validatePlaceholders(content, purpose.getVariables());
        if (!invalid.isEmpty()) {
            throw new InvalidSystemMessageTemplateException("The template " + field
                    + " uses placeholders this message cannot fill: " + String.join(", ", invalid)
                    + ". Available: " + available(purpose) + ".");
        }
    }

    private void requireVariable(SystemTemplatePurpose purpose, String content, String field) {
        String required = purpose.getRequiredVariable();
        if (!isBlank(content) && !MessageTemplateParser.containsPlaceholder(content, required)) {
            throw new InvalidSystemMessageTemplateException("The template " + field
                    + " must include #{" + required + "}, otherwise the message goes out without it.");
        }
    }

    private String available(SystemTemplatePurpose purpose) {
        return purpose.getVariables().stream().sorted()
                .map(name -> "#{" + name + "}")
                .collect(Collectors.joining(", "));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
