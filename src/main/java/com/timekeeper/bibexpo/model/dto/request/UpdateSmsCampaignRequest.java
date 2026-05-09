package com.timekeeper.bibexpo.model.dto.request;

import com.timekeeper.bibexpo.model.enums.SmsCampaignTargetFilter;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for updating an SMS campaign")
public class UpdateSmsCampaignRequest {

    @Size(max = 100, message = "Campaign name must not exceed 100 characters")
    @Schema(description = "Human readable name for the campaign", example = "Bib Collection Confirmation")
    private String name;

    @Schema(description = "ID of the SMS template to use", example = "1")
    private Long smsTemplateId;

    @Schema(description = "AUTO_BIB_COLLECTED: fires per participant on bib collection. SCHEDULED: fires once at scheduledAt. MANUAL: triggered by an operator.", example = "SCHEDULED")
    private SmsCampaignTriggerType triggerType;

    @Schema(description = "ALL: every participant. NOT_COLLECTED: only participants who have not yet collected their bib.", example = "ALL")
    private SmsCampaignTargetFilter targetFilter;

    @Schema(description = "Required when triggerType is or becomes SCHEDULED. Ignored and cleared for all other trigger types.", example = "2026-01-20T09:00:00")
    private LocalDateTime scheduledAt;
}
