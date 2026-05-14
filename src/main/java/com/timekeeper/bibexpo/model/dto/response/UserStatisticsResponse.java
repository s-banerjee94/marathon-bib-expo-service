package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.dto.response.stats.UserStatsData;
import com.timekeeper.bibexpo.model.enums.StatisticsScope;
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
@Schema(description = "Role-scoped user statistics snapshot")
public class UserStatisticsResponse {

    @Schema(description = "GLOBAL for ROOT/ADMIN, ORGANIZATION for org roles", example = "GLOBAL")
    private StatisticsScope scope;

    @Schema(description = "Organization ID — null for GLOBAL scope", example = "5")
    private Long organizationId;

    @Schema(description = "Organization name — null for GLOBAL scope", example = "ABC Marathon Club")
    private String organizationName;

    @Schema(description = "When this snapshot was last refreshed", example = "2026-02-23T10:00:00Z")
    private Instant refreshedAt;

    @Schema(description = "User statistics breakdown")
    private UserStatsData users;
}
