package com.timekeeper.bibexpo.messaging.campaign.model.dto.response;

import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsTemplate;
import com.timekeeper.bibexpo.messaging.campaign.util.TemplateSenderStamp;
import com.timekeeper.bibexpo.messaging.provider.model.enums.ProviderSource;
import com.timekeeper.bibexpo.messaging.provider.model.enums.TemplateMode;
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
@Schema(description = "SMS Template response payload")
public class SmsTemplateResponse {

    @Schema(description = "SMS Template ID", example = "1")
    private Long id;

    @Schema(description = "Human-readable name for the template", example = "bib collection reminder")
    private String name;

    @Schema(description = "DLT Template ID from telecom provider", example = "1107161234567890123")
    private String smsTemplateId;

    @Schema(description = "Registered DLT header / sender id", example = "BIBEXP")
    private String senderId;

    @Schema(description = "SMS template text with placeholders", example = "Hi {participantName}, your bib #{bibNumber} is ready at {venueName} for {eventName}!")
    private String template;

    @Schema(description = "Ordered variable expressions for a provider-rendered SMS provider; entry n fills {{VAR:n}}")
    private List<String> bodyVariables;

    @Schema(description = "Rendering this template was authored for, taken from the provider at creation. CLIENT_RENDERED: the message text is edited here. PROVIDER_RENDERED: the provider holds the registered template and only the variables are edited here. Read this to shape the edit form — it does not change when the organization switches provider.",
            example = "CLIENT_RENDERED")
    private TemplateMode renderMode;

    @Schema(description = "Which sender this template was written against: ORGANIZATION for the organization's own, "
            + "DEFAULT for the platform one. Sending is refused when the sender in force is the other source, since a "
            + "registered template id belongs to one vendor account. Null on templates written before this was recorded.",
            example = "DEFAULT")
    private ProviderSource providerSource;

    @Schema(description = "Optional note or description", example = "Reminder to collect bib at expo")
    private String note;

    @Schema(description = "Event ID associated with this template", example = "1")
    private Long eventId;

    @Schema(description = "Organization ID that owns the parent event", example = "1")
    private Long organizationId;

    @Schema(description = "Event name for context", example = "Mumbai Marathon 2024")
    private String eventName;

    @Schema(description = "Creation timestamp", example = "2026-01-15T10:30:00Z")
    private Instant createdAt;

    @Schema(description = "Last update timestamp", example = "2026-01-15T10:30:00Z")
    private Instant updatedAt;

    @Schema(description = "Created by username", example = "admin")
    private String createdBy;

    @Schema(description = "Last modified by username", example = "admin")
    private String lastModifiedBy;

    /**
     * Factory method to create SmsTemplateResponse from SmsTemplate entity
     */
    public static SmsTemplateResponse fromEntity(SmsTemplate smsTemplate) {
        return SmsTemplateResponse.builder()
                .id(smsTemplate.getId())
                .name(smsTemplate.getName())
                .smsTemplateId(smsTemplate.getSmsTemplateId())
                .senderId(smsTemplate.getSenderId())
                .template(smsTemplate.getTemplate())
                .bodyVariables(splitBodyVariables(smsTemplate.getBodyVariables()))
                // Rows saved before the stamp existed are read from their content, so the client always
                // gets a mode to shape the editor with.
                .renderMode(smsTemplate.getRenderMode() != null ? smsTemplate.getRenderMode()
                        : TemplateSenderStamp.fromSmsContent(smsTemplate.getTemplate(), smsTemplate.getBodyVariables()))
                .providerSource(smsTemplate.getProviderSource())
                .note(smsTemplate.getNote())
                .eventId(smsTemplate.getEvent() != null ? smsTemplate.getEvent().getId() : null)
                .organizationId(smsTemplate.getEvent() != null && smsTemplate.getEvent().getOrganization() != null
                        ? smsTemplate.getEvent().getOrganization().getId() : null)
                .eventName(smsTemplate.getEvent() != null ? smsTemplate.getEvent().getEventName() : null)
                .createdAt(smsTemplate.getCreatedAt())
                .updatedAt(smsTemplate.getUpdatedAt())
                .createdBy(smsTemplate.getCreatedBy())
                .lastModifiedBy(smsTemplate.getLastModifiedBy())
                .build();
    }

    private static List<String> splitBodyVariables(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return List.of(joined.split("\n"));
    }
}
