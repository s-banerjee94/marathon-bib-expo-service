package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.entity.EventLimit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Per-event resource limits")
public class EventLimitResponse {

    @Schema(description = "Event ID", example = "1")
    private Long eventId;

    @Schema(description = "Maximum number of participants", example = "50000")
    private Integer maxParticipants;

    @Schema(description = "Maximum number of races", example = "20")
    private Integer maxRaces;

    @Schema(description = "Maximum number of categories per race", example = "50")
    private Integer maxCategoriesPerRace;

    @Schema(description = "Maximum number of goodies types", example = "15")
    private Integer maxGoodies;

    @Schema(description = "Maximum number of SMS templates", example = "20")
    private Integer maxSmsTemplates;

    @Schema(description = "Maximum number of SMS campaigns", example = "20")
    private Integer maxSmsCampaigns;

    @Schema(description = "Maximum number of full imports", example = "10")
    private Integer maxImports;

    @Schema(description = "Maximum number of add-on imports", example = "5")
    private Integer maxAddOns;

    public static EventLimitResponse fromEntity(EventLimit limit) {
        return EventLimitResponse.builder()
                .eventId(limit.getEventId())
                .maxParticipants(limit.getMaxParticipants())
                .maxRaces(limit.getMaxRaces())
                .maxCategoriesPerRace(limit.getMaxCategoriesPerRace())
                .maxGoodies(limit.getMaxGoodies())
                .maxSmsTemplates(limit.getMaxSmsTemplates())
                .maxSmsCampaigns(limit.getMaxSmsCampaigns())
                .maxImports(limit.getMaxImports())
                .maxAddOns(limit.getMaxAddOns())
                .build();
    }
}
