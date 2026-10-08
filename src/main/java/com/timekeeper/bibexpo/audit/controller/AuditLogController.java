package com.timekeeper.bibexpo.audit.controller;

import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.audit.model.dto.response.AuditLogListResponse;
import com.timekeeper.bibexpo.audit.service.AuditService;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
public class AuditLogController implements AuditLogControllerApi {

    private final AuditService auditService;

    @Override
    public ResponseEntity<AuditLogListResponse> getAuditLogs(
            Long organizationId,
            Instant from,
            Instant to,
            AuditAction action,
            AuditEntityType entityType,
            String username,
            int limit,
            String lastEvaluatedKey,
            User currentUser) {
        return ResponseEntity.ok(auditService.getAuditLogs(
                organizationId, from, to, action, entityType, username,
                limit, lastEvaluatedKey, currentUser));
    }
}
