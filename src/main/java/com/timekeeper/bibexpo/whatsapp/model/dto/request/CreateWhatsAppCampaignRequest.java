package com.timekeeper.bibexpo.whatsapp.model.dto.request;

import com.timekeeper.bibexpo.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.model.enums.CampaignTriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        Request payload for creating a WhatsApp campaign. \
        If triggerType is omitted the campaign is saved as DRAFT. \
        If triggerType is present the campaign is armed and moves directly to ACTIVE.""")
public class CreateWhatsAppCampaignRequest {

    @NotBlank(message = "Campaign name is required.")
    @Size(max = 100, message = "Campaign name must not exceed 100 characters.")
    @Schema(description = "Human readable name for the campaign", example = "Bib Collection Confirmation")
    private String name;

    @NotNull(message = "WhatsApp template ID is required.")
    @Schema(description = "ID of the WhatsApp template to use", example = "1")
    private Long whatsAppTemplateId;

    @Schema(description = "Set to arm the campaign immediately. AUTO_BIB_COLLECTED: fires per participant on bib collection. SCHEDULED: fires once at scheduledAt.", example = "SCHEDULED")
    private CampaignTriggerType triggerType;

    @Schema(description = "Required when triggerType is present. ALL: every participant. NOT_COLLECTED: only participants who have not yet collected their bib.", example = "ALL")
    private CampaignTargetFilter targetFilter;

    @Schema(description = "Date for scheduled send (SCHEDULED type only), format yyyy-MM-dd.", example = "2026-07-20")
    private String scheduledDate;

    @Schema(description = "Time for scheduled send (SCHEDULED type only), format HH:mm. Must be at least 3 minutes in the future relative to the event timezone.", example = "09:00")
    private String scheduledTime;
}
