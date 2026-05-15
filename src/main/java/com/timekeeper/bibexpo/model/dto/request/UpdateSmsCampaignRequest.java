package com.timekeeper.bibexpo.model.dto.request;

import com.timekeeper.bibexpo.model.enums.SmsCampaignTargetFilter;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = """
        Request payload for updating a DRAFT campaign. \
        If triggerType is present the campaign is armed and moves to ACTIVE. \
        If triggerType is null only name and template are updated and the campaign stays DRAFT.""")
public class UpdateSmsCampaignRequest {

    @Size(max = 100, message = "Campaign name must not exceed 100 characters")
    @Schema(description = "Human readable name for the campaign", example = "Bib Collection Confirmation")
    private String name;

    @Schema(description = "ID of the SMS template to use", example = "12546")
    private Long smsTemplateId;

    @Schema(description = "Set to arm the campaign. AUTO_BIB_COLLECTED: fires per participant on bib collection. SCHEDULED: fires once at the scheduled date and time.", example = "SCHEDULED")
    private SmsCampaignTriggerType triggerType;

    @Schema(description = "Required when triggerType is present. ALL: every participant. NOT_COLLECTED: only participants who have not yet collected their bib.", example = "ALL")
    private SmsCampaignTargetFilter targetFilter;

    @Schema(description = "Date for scheduled send (SCHEDULED type only), format yyyy-MM-dd.", example = "2026-01-20")
    private String scheduledDate;

    @Schema(description = "Time for scheduled send (SCHEDULED type only), format HH:mm. Must be at least 3 minutes in the future relative to the event timezone.", example = "09:00")
    private String scheduledTime;
}
