package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.dynamodb.AuditLogDDB;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Single audit log entry for the Recent Activity feed")
public class AuditLogResponse {

    @Schema(description = "Audit log entry ID (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;

    @Schema(description = "Action performed", example = "CREATE")
    private AuditAction action;

    @Schema(description = "Type of entity acted on", example = "EVENT")
    private AuditEntityType entityType;

    @Schema(description = "ID of the entity acted on", example = "42")
    private String entityId;

    @Schema(description = "Display name of the entity at the time of the action", example = "Pune Run Fest 2025")
    private String entityLabel;

    @Schema(description = "Display name of the user who performed the action", example = "Rahul Sharma")
    private String actorName;

    @Schema(description = "Human-readable description for the feed", example = "Event \"Pune Run Fest 2025\" created")
    private String description;

    @Schema(description = "When this action occurred", example = "2026-05-10T09:30:00Z")
    private Instant createdAt;

    public static AuditLogResponse fromDdb(AuditLogDDB ddb) {
        return AuditLogResponse.builder()
                .id(ddb.getId())
                .action(ddb.getAction() != null ? AuditAction.valueOf(ddb.getAction()) : null)
                .entityType(ddb.getEntityType() != null ? AuditEntityType.valueOf(ddb.getEntityType()) : null)
                .entityId(ddb.getEntityId())
                .entityLabel(ddb.getEntityLabel())
                .actorName(ddb.getActorName())
                .description(ddb.getDescription())
                .createdAt(ddb.getCreatedAt() != null ? Instant.parse(ddb.getCreatedAt()) : null)
                .build();
    }
}
