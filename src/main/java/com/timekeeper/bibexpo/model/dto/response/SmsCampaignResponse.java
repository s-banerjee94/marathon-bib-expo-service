package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.model.enums.SmsCampaignStatus;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTargetFilter;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "SMS Campaign response payload")
public class SmsCampaignResponse {

    @Schema(description = "Campaign ID", example = "1")
    private Long id;

    @Schema(description = "Campaign name", example = "Bib Collection Confirmation")
    private String name;

    @Schema(description = "Event ID", example = "10")
    private Long eventId;

    @Schema(description = "Event name", example = "Mumbai Marathon 2026")
    private String eventName;

    @Schema(description = "Linked SMS template ID", example = "1")
    private Long smsTemplateId;

    @Schema(description = "Linked SMS template name", example = "bib confirmation")
    private String smsTemplateName;

    @Schema(description = "How the campaign is triggered", example = "AUTO_BIB_COLLECTED")
    private SmsCampaignTriggerType triggerType;

    @Schema(description = "Who receives the SMS", example = "ALL")
    private SmsCampaignTargetFilter targetFilter;

    @Schema(description = "Scheduled send time (SCHEDULED type only)", example = "2026-01-20T09:00:00")
    private LocalDateTime scheduledAt;

    @Schema(description = "Campaign lifecycle status", example = "ACTIVE")
    private SmsCampaignStatus status;

    @Schema(description = "Number of SMS messages sent", example = "4983")
    private Integer sentCount;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @Schema(description = "Created by username", example = "admin")
    private String createdBy;

    @Schema(description = "Last modified by username", example = "admin")
    private String lastModifiedBy;

    /**
     * Factory method to create SmsCampaignResponse from SmsCampaign entity
     */
    public static SmsCampaignResponse fromEntity(SmsCampaign campaign) {
        return SmsCampaignResponse.builder()
                .id(campaign.getId())
                .name(campaign.getName())
                .eventId(campaign.getEvent() != null ? campaign.getEvent().getId() : null)
                .eventName(campaign.getEvent() != null ? campaign.getEvent().getEventName() : null)
                .smsTemplateId(campaign.getSmsTemplate() != null ? campaign.getSmsTemplate().getId() : null)
                .smsTemplateName(campaign.getSmsTemplate() != null ? campaign.getSmsTemplate().getName() : null)
                .triggerType(campaign.getTriggerType())
                .targetFilter(campaign.getTargetFilter())
                .scheduledAt(campaign.getScheduledAt())
                .status(campaign.getStatus())
                .sentCount(campaign.getSentCount())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .createdBy(campaign.getCreatedBy())
                .lastModifiedBy(campaign.getLastModifiedBy())
                .build();
    }
}
