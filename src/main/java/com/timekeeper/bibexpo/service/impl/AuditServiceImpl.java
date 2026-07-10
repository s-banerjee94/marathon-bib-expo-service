package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.InvalidUserDataException;
import com.timekeeper.bibexpo.exception.AccessForbiddenException;
import com.timekeeper.bibexpo.model.dto.audit.AuditLogQuery;
import com.timekeeper.bibexpo.model.dto.response.AuditLogListResponse;
import com.timekeeper.bibexpo.model.dto.response.AuditLogResponse;
import com.timekeeper.bibexpo.model.dynamodb.AuditLogDDB;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.repository.UserRepository;
import com.timekeeper.bibexpo.repository.dynamodb.AuditLogDDBRepository;
import com.timekeeper.bibexpo.service.AuditService;
import com.timekeeper.bibexpo.service.util.DynamoDBPaginationCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final AuditLogDDBRepository auditLogRepository;
    private final UserRepository userRepository;
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

        validateFilters(from, to, action, entityType, username);

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
        Map<String, AttributeValue> startKey = unwrapCursor(cursor, scope);
        Page<AuditLogDDB> page = auditLogRepository.query(query, limit, startKey);
        return toResponse(page, scope);
    }

    /**
     * Only one indexed dimension can back a single Query, so reject combinations that would
     * otherwise need in-app filtering.
     */
    private void validateFilters(Instant from, Instant to, AuditAction action,
                                 AuditEntityType entityType, String username) {
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
            User user = userRepository.findByUsernameWithOrganization(currentUser.getUsername())
                    .orElseThrow(() -> new AccessForbiddenException("Your account could not be found."));
            if (user.getOrganization() == null) {
                throw new AccessForbiddenException("Your account is not assigned to an organization.");
            }
            return user.getOrganization().getId();
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
        String nextCursor = wrapCursor(page.lastEvaluatedKey(), scope);
        boolean hasMore = nextCursor != null;
        return AuditLogListResponse.builder()
                .items(items)
                .count(items.size())
                .lastEvaluatedKey(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    /**
     * Cursors are scope-tagged with the partition + filter dimension that issued them. Reusing a
     * cursor against a different scope (different org, switched filter, etc.) would yield a
     * DynamoDB ValidationException or empty pages — reject it with a clear 400 instead.
     */
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
        return q.getOrganizationId() + "|" + dim;
    }

    private String wrapCursor(Map<String, AttributeValue> raw, String scope) {
        String encoded = paginationCodec.encode(raw);
        if (encoded == null || encoded.isEmpty()) return null;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((scope + "|" + encoded).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, AttributeValue> unwrapCursor(String cursor, String scope) {
        if (cursor == null || cursor.isBlank()) return Map.of();
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidUserDataException("Your pagination cursor is invalid. Start a new search.");
        }
        int sep = decoded.indexOf('|', decoded.indexOf('|') + 1);
        if (sep < 0) {
            throw new InvalidUserDataException("Your pagination cursor is invalid. Start a new search.");
        }
        String storedScope = decoded.substring(0, sep);
        if (!scope.equals(storedScope)) {
            throw new InvalidUserDataException("Your pagination cursor is from a different view. Start a new search.");
        }
        return paginationCodec.decode(decoded.substring(sep + 1));
    }
}
