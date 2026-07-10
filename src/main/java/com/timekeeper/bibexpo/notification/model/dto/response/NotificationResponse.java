package com.timekeeper.bibexpo.notification.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single in-app notification")
public class NotificationResponse {

    @Schema(description = "Opaque notification id — pass to the mark-as-read endpoint", example = "MjAyNi0wNi0xNlQwODozMDowMFojYTFiMmMz")
    private String id;

    @Schema(
            description = """
                    Notification category — drives the icon and the deep-link route on the frontend. Catalog:
                    - `IMPORT_COMPLETED` / `IMPORT_FAILED` — CSV participant import finished (entity: EVENT).
                    - `SHORT_URLS_COMPLETED` — bulk verification-link generation finished (entity: EVENT).
                    - `EVENT_CREATED` — an organizer created an event; sent to ROOT/ADMIN (entity: EVENT).
                    - `EVENT_PUBLISHED` / `EVENT_COMPLETED` — event went live / was completed; sent to ROOT/ADMIN (entity: EVENT).
                    - `EVENT_CANCELLED` — event cancelled; sent to the organization's staff (entity: EVENT).
                    - `CAMPAIGN_COMPLETED` / `CAMPAIGN_FAILED` — SMS/WhatsApp campaign lifecycle (entity: CAMPAIGN).""",
            allowableValues = {
                    "IMPORT_COMPLETED", "IMPORT_FAILED", "SHORT_URLS_COMPLETED",
                    "EVENT_CREATED", "EVENT_PUBLISHED", "EVENT_CANCELLED", "EVENT_COMPLETED",
                    "CAMPAIGN_COMPLETED", "CAMPAIGN_FAILED"
            },
            example = "EVENT_CREATED")
    private String type;

    @Schema(description = "Short headline", example = "New Event Created")
    private String title;

    @Schema(description = "Body text", example = "organiserA created a new event \"City Run 2026\" (Acme Sports).")
    private String message;

    @Schema(description = "Whether the recipient has read it", example = "false")
    private Boolean read;

    @Schema(description = "Deep-link target type (nullable). Pair with `entityId` to route on click.",
            allowableValues = {"EVENT", "CAMPAIGN"}, example = "EVENT")
    private String entityType;

    @Schema(description = "Deep-link target id (nullable)", example = "42")
    private String entityId;

    @Schema(description = "Username of whoever triggered it (nullable)", example = "organiserA")
    private String actorName;

    @Schema(description = "Creation time, ISO-8601 UTC", example = "2026-06-16T08:30:00Z")
    private String createdAt;
}
