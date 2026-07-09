package com.timekeeper.bibexpo.messaging.campaign.model.dto.response;

import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppCampaign;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "WhatsApp campaign response payload")
public class WhatsAppCampaignResponse {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Schema(description = "Campaign ID", example = "1")
    private Long id;

    @Schema(description = "Campaign name", example = "Bib Collection Confirmation")
    private String name;

    @Schema(description = "Event ID", example = "10")
    private Long eventId;

    @Schema(description = "Organization ID that owns the parent event", example = "1")
    private Long organizationId;

    @Schema(description = "Event name", example = "Mumbai Marathon 2026")
    private String eventName;

    @Schema(description = "Linked WhatsApp template ID", example = "1")
    private Long whatsAppTemplateId;

    @Schema(description = "Linked WhatsApp template name", example = "bib confirmation")
    private String whatsAppTemplateName;

    @Schema(description = "How the campaign is triggered", example = "AUTO_BIB_COLLECTED")
    private CampaignTriggerType triggerType;

    @Schema(description = "Who receives the message", example = "ALL")
    private CampaignTargetFilter targetFilter;

    @Schema(description = "Scheduled send date in event timezone (SCHEDULED type only)", example = "2026-07-20")
    private String scheduledDate;

    @Schema(description = "Scheduled send time in event timezone (SCHEDULED type only)", example = "09:00")
    private String scheduledTime;

    @Schema(description = "Campaign lifecycle status: DRAFT = saved, not yet armed; ACTIVE = armed and running; SENDING = batch dispatch in progress; SENT = completed; FAILED = dispatch failed after max retries", example = "ACTIVE")
    private CampaignStatus status;

    @Schema(description = "Number of WhatsApp messages sent", example = "4983")
    private Integer sentCount;

    @Schema(description = "Number of send attempts made by the scheduler (0 = not yet retried)", example = "0")
    private Integer retryCount;

    @Schema(description = "Creation timestamp", example = "2026-06-10T10:30:00Z")
    private Instant createdAt;

    @Schema(description = "Last update timestamp", example = "2026-06-10T10:30:00Z")
    private Instant updatedAt;

    @Schema(description = "Created by username", example = "admin")
    private String createdBy;

    @Schema(description = "Last modified by username", example = "admin")
    private String lastModifiedBy;

    public static WhatsAppCampaignResponse fromEntity(WhatsAppCampaign campaign, Event event) {
        String scheduledDate = null;
        String scheduledTime = null;
        if (campaign.getScheduledAt() != null && event != null && event.getTimezone() != null) {
            ZonedDateTime zdt = campaign.getScheduledAt().atZone(ZoneId.of(event.getTimezone()));
            scheduledDate = zdt.toLocalDate().toString();
            scheduledTime = zdt.format(TIME_FMT);
        }
        return WhatsAppCampaignResponse.builder()
                .id(campaign.getId())
                .name(campaign.getName())
                .eventId(campaign.getEventId())
                .organizationId(campaign.getOrganizationId())
                .eventName(event != null ? event.getEventName() : null)
                .whatsAppTemplateId(campaign.getWhatsAppTemplate() != null ? campaign.getWhatsAppTemplate().getId() : null)
                .whatsAppTemplateName(campaign.getWhatsAppTemplate() != null ? campaign.getWhatsAppTemplate().getName() : null)
                .triggerType(campaign.getTriggerType())
                .targetFilter(campaign.getTargetFilter())
                .scheduledDate(scheduledDate)
                .scheduledTime(scheduledTime)
                .status(campaign.getStatus())
                .sentCount(campaign.getSentCount())
                .retryCount(campaign.getRetryCount())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .createdBy(campaign.getCreatedBy())
                .lastModifiedBy(campaign.getLastModifiedBy())
                .build();
    }
}
