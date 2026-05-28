package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.response.AuditLogListResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@Slf4j
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
        log.info("GET /audit-logs by: {} role: {}", currentUser.getUsername(), currentUser.getRole());
        return ResponseEntity.ok(auditService.getAuditLogs(
                organizationId, from, to, action, entityType, username,
                limit, lastEvaluatedKey, currentUser));
    }
}
