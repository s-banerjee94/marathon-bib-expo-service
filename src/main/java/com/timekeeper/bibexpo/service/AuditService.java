package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.response.AuditLogListResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;

import java.time.Instant;

/**
 * Read-side of the audit log. Writes are produced by {@code AuditAspect} via the
 * active {@link com.timekeeper.bibexpo.service.audit.AuditPublisher}.
 */
public interface AuditService {

    /**
     * Returns one page of audit logs, newest first, as a single direct DynamoDB Query.
     * At most one indexed dimension applies per call — {@code action}, {@code entityType},
     * or {@code username} — optionally narrowed by a date range. There is no in-app
     * filtering or cross-partition merging.
     *
     * <p>Scope rules:
     * <ul>
     *   <li>Organizer roles: always scoped to their own organization (any supplied
     *       {@code organizationId} is ignored).</li>
     *   <li>ROOT/ADMIN: must pass {@code organizationId} — a single partition is queried.</li>
     * </ul>
     */
    AuditLogListResponse getAuditLogs(Long organizationId,
                                      Instant from,
                                      Instant to,
                                      AuditAction action,
                                      AuditEntityType entityType,
                                      String username,
                                      int limit,
                                      String cursor,
                                      User currentUser);
}
