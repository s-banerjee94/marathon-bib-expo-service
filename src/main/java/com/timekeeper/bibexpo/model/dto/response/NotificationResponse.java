package com.timekeeper.bibexpo.model.dto.response;

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
@Schema(description = "In-app notification")
public class NotificationResponse {

    @Schema(description = "Notification ID", example = "1")
    private Long id;

    @Schema(description = "Notification title", example = "CSV Import Completed")
    private String title;

    @Schema(description = "Notification message", example = "Imported 500 participants (2 skipped)")
    private String message;

    @Schema(description = "Whether the notification has been read", example = "false")
    private Boolean read;

    @Schema(description = "Related event ID", example = "42")
    private Long eventId;

    @Schema(description = "Related batch job execution ID", example = "7")
    private Long jobExecutionId;

    @Schema(description = "Timestamp when notification was created", example = "2026-01-15T10:30:00Z")
    private Instant createdAt;
}
