package com.timekeeper.bibexpo.model.dto.response.stats;

import com.timekeeper.bibexpo.model.entity.UserRole;
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
@Schema(description = "User statistics breakdown")
public class UserStatsData {

    @Schema(description = "Total non-deleted users", example = "150")
    private long total;

    @Schema(description = "Active (enabled) users", example = "130")
    private long active;

    @Schema(description = "Inactive (disabled) users", example = "20")
    private long inactive;

    @Schema(description = "User count per role")
    private Map<UserRole, Long> byRole;
}
