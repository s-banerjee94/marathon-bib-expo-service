package com.timekeeper.bibexpo.model.dto.response.stats;

import com.timekeeper.bibexpo.model.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Event statistics breakdown")
public class EventStatsData {

    @Schema(description = "Total events in scope", example = "42")
    private long total;

    @Schema(description = "Upcoming published events (start date in the future)", example = "8")
    private long upcoming;

    @Schema(description = "Event count per status")
    private Map<EventStatus, Long> byStatus;
}
