package com.timekeeper.bibexpo.audit.service.impl;

import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.audit.model.dto.AuditLogQuery;
import com.timekeeper.bibexpo.audit.model.dto.response.AuditLogListResponse;
import com.timekeeper.bibexpo.audit.model.dto.response.AuditLogResponse;
import com.timekeeper.bibexpo.audit.model.dynamodb.AuditLogDDB;
import com.timekeeper.bibexpo.audit.repository.AuditLogDDBRepository;
import com.timekeeper.bibexpo.audit.service.AuditService;
import com.timekeeper.bibexpo.shared.error.AccessForbiddenException;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.shared.persistence.DynamoDBPaginationCodec;
import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogDDBRepository auditLogRepository;
    private final DynamoDBPaginationCodec paginationCodec;

    @Override
    public AuditLogListResponse getAuditLogs(Long organizationId,
                                             Instant from,
                                             Instant to,
                                             AuditAction action,
                                             AuditEntityType entityType,
                                             String username,
                                             int limit,
                                             String cursor,
                                             User currentUser) {

        validateFilters(from, to, action, entityType, username, limit);

        boolean globalRole = currentUser.getRole() == UserRole.ROOT || currentUser.getRole() == UserRole.ADMIN;
        Long partitionKey = resolveScope(globalRole, organizationId, currentUser);

        AuditLogQuery query = AuditLogQuery.builder()
                .organizationId(partitionKey)
                .from(from)
                .to(to)
                .action(action)
                .entityType(entityType)
                .username(username)
                .build();

        String scope = scopeTag(query);
        Map<String, AttributeValue> startKey = paginationCodec.decode(cursor, scope);
        Page<AuditLogDDB> page = auditLogRepository.query(query, limit, startKey);
        return toResponse(page, scope);
    }

    /**
     * Only one indexed dimension can back a single Query, so reject combinations that would
     * otherwise need in-app filtering.
     */
    private void validateFilters(Instant from, Instant to, AuditAction action,
                                 AuditEntityType entityType, String username, int limit) {
        if (limit < 1) {
            throw new InvalidUserDataException("Please ask for at least one entry per page.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidUserDataException("The start date must be on or before the end date.");
        }
        int dimensions = 0;
        if (action != null) dimensions++;
        if (entityType != null) dimensions++;
        if (username != null && !username.isBlank()) dimensions++;
        if (dimensions > 1) {
            throw new InvalidUserDataException("Please filter by only one of action, entity type, or user at a time.");
        }
    }

    private Long resolveScope(boolean globalRole, Long organizationId, User currentUser) {
        if (!globalRole) {
            if (currentUser.getOrganization() == null) {
                throw new AccessForbiddenException("Your account is not assigned to an organization.");
            }
            return currentUser.getOrganization().getId();
        }
        return organizationId != null ? organizationId : AuditLogDDB.ALL_PARTITION;
    }

    private AuditLogListResponse toResponse(Page<AuditLogDDB> page, String scope) {
        if (page == null) {
            return AuditLogListResponse.builder().items(List.of()).count(0).hasMore(false).build();
        }
        List<AuditLogResponse> items = page.items().stream()
                .map(AuditLogResponse::fromDdb)
                .toList();
        String nextCursor = paginationCodec.encode(page.lastEvaluatedKey(), scope);
        boolean hasMore = nextCursor != null;
        return AuditLogListResponse.builder()
                .items(items)
                .count(items.size())
                .lastEvaluatedKey(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    // The partition and filter dimension that shape the query, so a cursor from another view is refused.
    private String scopeTag(AuditLogQuery q) {
        String dim;
        if (q.getUsername() != null && !q.getUsername().isBlank()) {
            dim = "u:" + q.getUsername();
        } else if (q.getAction() != null) {
            dim = "a:" + q.getAction().name();
        } else if (q.getEntityType() != null) {
            dim = "e:" + q.getEntityType().name();
        } else {
            dim = "t";
        }
        return "audit|" + q.getOrganizationId() + "|" + dim;
    }
}
