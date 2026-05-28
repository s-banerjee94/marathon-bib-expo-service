package com.timekeeper.bibexpo.service.audit.impl;

import com.timekeeper.bibexpo.model.dto.audit.AuditEvent;
import com.timekeeper.bibexpo.model.dynamodb.AuditLogDDB;
import com.timekeeper.bibexpo.repository.dynamodb.AuditLogDDBRepository;
import com.timekeeper.bibexpo.service.audit.AuditPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;

@Component
@Slf4j
public class DynamoDbAuditPublisher implements AuditPublisher {

    /** Items auto-expire 15 days after creation via the {@code expirationTime} TTL attribute. */
    private static final long TTL_SECONDS = 15L * 24 * 60 * 60;

    private final AuditLogDDBRepository repository;
    private final Executor executor;

    public DynamoDbAuditPublisher(AuditLogDDBRepository repository,
                                  @Qualifier("auditTaskExecutor") Executor executor) {
        this.repository = repository;
        this.executor = executor;
    }

    /**
     * Dual-write: one row to the org partition and one to {@link AuditLogDDB#ALL_PARTITION}
     * so ROOT/ADMIN can read a sorted cross-org feed with a single Query. Both rows share
     * the same {@code id} since they represent the same logical event.
     */
    @Override
    public void publish(AuditEvent event) {
        Instant now = event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now();
        String entryId = UUID.randomUUID().toString();
        Long orgPartition = event.getOrganizationId() != null ? event.getOrganizationId() : 0L;

        AuditLogDDB orgRow = toRow(event, orgPartition, entryId, now);
        AuditLogDDB allRow = toRow(event, AuditLogDDB.ALL_PARTITION, entryId, now);

        executor.execute(() -> {
            try {
                repository.save(orgRow);
                repository.save(allRow);
            } catch (Exception e) {
                log.error("Failed to persist audit entry: action={} entityType={} entityId={}",
                        event.getAction(), event.getEntityType(), event.getEntityId(), e);
            }
        });
    }

    private AuditLogDDB toRow(AuditEvent event, Long partition, String entryId, Instant now) {
        String uuidSuffix = entryId.substring(0, 8);
        String timeSuffix = now.toString() + "#" + uuidSuffix;

        String action = event.getAction() != null ? event.getAction().name() : null;
        String entityType = event.getEntityType() != null ? event.getEntityType().name() : null;
        String actorName = event.getActorName();

        return AuditLogDDB.builder()
                .organizationId(partition)
                .eventKey(timeSuffix)
                .actionKey(action != null ? action + "#" + timeSuffix : null)
                .entityTypeKey(entityType != null ? entityType + "#" + timeSuffix : null)
                .actorKey(actorName != null ? actorName + "#" + timeSuffix : null)
                .id(entryId)
                .actorUserId(event.getActorUserId())
                .actorName(actorName)
                .action(action)
                .entityType(entityType)
                .entityId(event.getEntityId())
                .entityLabel(event.getEntityLabel())
                .description(event.getDescription())
                .createdAt(now.toString())
                .expirationTime(now.getEpochSecond() + TTL_SECONDS)
                .build();
    }
}
