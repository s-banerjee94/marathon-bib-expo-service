package com.timekeeper.bibexpo.messaging.system.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.messaging.shared.exception.MessagingConfigNotFoundException;
import com.timekeeper.bibexpo.messaging.system.model.dto.request.SaveSystemMessageTemplateRequest;
import com.timekeeper.bibexpo.messaging.system.model.dto.response.SystemMessageTemplateResponse;
import com.timekeeper.bibexpo.messaging.system.service.SystemMessageTemplateAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SystemMessageTemplateController implements SystemMessageTemplateControllerApi {

    private final SystemMessageTemplateAdminService templateAdminService;

    @Override
    public ResponseEntity<List<SystemMessageTemplateResponse>> list() {
        return ResponseEntity.ok(templateAdminService.list());
    }

    @Override
    public ResponseEntity<SystemMessageTemplateResponse> get(SystemTemplatePurpose purpose, MessageChannel channel) {
        return ResponseEntity.ok(templateAdminService.get(purpose, channel));
    }

    @Override
    public ResponseEntity<SystemMessageTemplateResponse> save(SystemTemplatePurpose purpose, MessageChannel channel,
                                                              SaveSystemMessageTemplateRequest request) {
        log.info("Root saving {} {} template", purpose, channel);
        return ResponseEntity.ok(templateAdminService.save(purpose, channel, request));
    }

    @ExceptionHandler(MessagingConfigNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(MessagingConfigNotFoundException ex, WebRequest request) {
        log.warn("System message template not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request));
    }
}
