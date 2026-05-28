package com.timekeeper.bibexpo.model.dto.audit;

import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Wire-format audit event passed from {@code AuditAspect} to an {@code AuditPublisher}.
 * When the publisher swaps from DynamoDB to SQS this is the message body that travels over the queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    /** Affected organization (0 = system event with no specific org). */
    private Long organizationId;

    private Long actorUserId;
    private String actorName;

    private AuditAction action;
    private AuditEntityType entityType;
    private String entityId;
    private String entityLabel;

    private String description;
    private Instant occurredAt;
}
