package com.timekeeper.bibexpo.model.dto.response.dashboard;

import com.timekeeper.bibexpo.model.enums.DashboardRange;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Single rollup response for the organizer dashboard")
public class OrgDashboardResponse {

    @Schema(description = "Timestamp when this snapshot was computed", example = "2026-05-28T11:48:02Z")
    private Instant refreshedAt;

    @Schema(description = "Range filter applied to stat cards, byStatus, byCity", example = "ALL")
    private DashboardRange range;

    @Schema(description = "Organization profile")
    private OrgInfoDto organization;

    @Schema(description = "Event statistics and lists")
    private EventsDashboardDto events;

    @Schema(description = "User counts — always all-time, unaffected by range")
    private UserCountsDto users;

    @Schema(description = "Trend sparkline data")
    private TrendsDto trends;
}
