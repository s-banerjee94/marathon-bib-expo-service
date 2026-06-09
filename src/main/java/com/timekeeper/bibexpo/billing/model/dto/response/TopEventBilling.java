package com.timekeeper.bibexpo.billing.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One event's billed rollup for the top-events leaderboard")
public class TopEventBilling {

    @Schema(description = "Event id", example = "42")
    private Long eventId;

    @Schema(description = "Event name captured on the bills", example = "Tata Steel Run")
    private String eventName;

    @Schema(description = "Organizer name captured on the bills", example = "Tata Steel")
    private String organizerName;

    @Schema(description = "Gross billed", example = "1245600.00")
    private BigDecimal billed;
}
