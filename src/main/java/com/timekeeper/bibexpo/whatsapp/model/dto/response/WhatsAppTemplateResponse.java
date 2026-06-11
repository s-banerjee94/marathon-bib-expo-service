package com.timekeeper.bibexpo.whatsapp.model.dto.response;

import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.whatsapp.model.entity.WhatsAppTemplate;
import com.timekeeper.bibexpo.whatsapp.model.enums.WhatsAppSenderScope;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "WhatsApp template response payload")
public class WhatsAppTemplateResponse {

    @Schema(description = "Template ID", example = "1")
    private Long id;

    @Schema(description = "Human-readable name for the template", example = "bib collection confirmation")
    private String name;

    @Schema(description = "Twilio Content SID of the approved template", example = "HX1234567890abcdef1234567890abcdef")
    private String contentSid;

    @Schema(description = "The stored template body with positional {{n}} markers",
            example = "Hi {{1}}, your bib {{2}} is ready for collection at {{3}}.")
    private String body;

    @Schema(description = "Ordered variable expressions; entry n fills the Twilio template variable {{n}}")
    private List<String> bodyVariables;

    @Schema(description = "Optional note or description")
    private String note;

    @Schema(description = "Which Twilio account the Content SID lives under", example = "DEFAULT")
    private WhatsAppSenderScope senderScope;

    @Schema(description = "Event ID associated with this template", example = "1")
    private Long eventId;

    @Schema(description = "Organization ID that owns the parent event", example = "1")
    private Long organizationId;

    @Schema(description = "Event name for context", example = "Mumbai Marathon 2026")
    private String eventName;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "Created by username", example = "admin")
    private String createdBy;

    @Schema(description = "Last modified by username", example = "admin")
    private String lastModifiedBy;

    public static WhatsAppTemplateResponse fromEntity(WhatsAppTemplate template, Event event) {
        return WhatsAppTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .contentSid(template.getContentSid())
                .body(template.getBody())
                .bodyVariables(splitBodyVariables(template.getBodyVariables()))
                .note(template.getNote())
                .senderScope(template.getSenderScope())
                .eventId(template.getEventId())
                .organizationId(template.getOrganizationId())
                .eventName(event != null ? event.getEventName() : null)
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .createdBy(template.getCreatedBy())
                .lastModifiedBy(template.getLastModifiedBy())
                .build();
    }

    private static List<String> splitBodyVariables(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return List.of(joined.split("\n"));
    }
}
