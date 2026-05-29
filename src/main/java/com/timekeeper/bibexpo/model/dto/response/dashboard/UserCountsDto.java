package com.timekeeper.bibexpo.model.dto.response.dashboard;

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
@Schema(description = "User counts block of the org dashboard — always all-time, unaffected by range")
public class UserCountsDto {

    @Schema(description = "Total users in the organization", example = "42")
    private long total;

    @Schema(description = "Enabled users", example = "40")
    private long active;

    @Schema(description = "Disabled users", example = "2")
    private long inactive;

    @Schema(description = "User counts grouped by role (ORGANIZER_ADMIN, ORGANIZER_USER, DISTRIBUTOR)")
    private Map<String, Long> byRole;
}
