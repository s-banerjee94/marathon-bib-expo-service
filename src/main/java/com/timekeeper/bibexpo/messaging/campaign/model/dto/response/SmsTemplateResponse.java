package com.timekeeper.bibexpo.messaging.campaign.model.dto.response;

import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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

    @Schema(description = "SMS template text with placeholders", example = "Hi {participantName}, your bib #{bibNumber} is ready at {venueName} for {eventName}!")
    private String template;

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
                .template(smsTemplate.getTemplate())
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
}
