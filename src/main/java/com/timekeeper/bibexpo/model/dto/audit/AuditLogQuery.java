package com.timekeeper.bibexpo.model.dto.audit;

import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Filter bag for audit log queries. Every query is a single direct DynamoDB Query: the
 * partition is the organization and the router picks one index from the populated dimension
 * (username, action, or entityType), each with an optional date range on the sort key.
 * No FilterExpression, no in-app filtering — at most one dimension applies.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogQuery {

    private Long organizationId;
    private Instant from;
    private Instant to;
    private AuditAction action;
    private AuditEntityType entityType;
    private String username;
}
