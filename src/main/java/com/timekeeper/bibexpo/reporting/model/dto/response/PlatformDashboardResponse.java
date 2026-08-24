package com.timekeeper.bibexpo.reporting.model.dto.response;

import com.timekeeper.bibexpo.shared.model.enums.DashboardRange;
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
@Schema(description = "Single rollup response for the ROOT/ADMIN platform overview dashboard")
public class PlatformDashboardResponse {

    @Schema(description = "Timestamp when this snapshot was computed", example = "2026-06-23T11:48:02Z")
    private Instant refreshedAt;

    @Schema(description = "Primary range filter applied to KPI cards and the range-able doughnuts", example = "ALL")
    private DashboardRange range;

    @Schema(description = "Organization stats block")
    private PlatformOrganizationsDto organizations;

    @Schema(description = "Event stats block")
    private PlatformEventsDto events;

    @Schema(description = "User counts — always all-time, unaffected by range")
    private PlatformUsersDto users;

    @Schema(description = "Platform growth trend — always all-time")
    private PlatformTrendsDto trends;
}
