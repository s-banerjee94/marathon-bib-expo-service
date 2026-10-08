package com.timekeeper.bibexpo.messaging.system.controller;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.messaging.system.model.dto.request.SaveSystemMessageTemplateRequest;
import com.timekeeper.bibexpo.messaging.system.model.dto.response.SystemMessageTemplateResponse;
import com.timekeeper.bibexpo.messaging.system.service.SystemMessageTemplateAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
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
        return ResponseEntity.ok(templateAdminService.save(purpose, channel, request));
    }
}
