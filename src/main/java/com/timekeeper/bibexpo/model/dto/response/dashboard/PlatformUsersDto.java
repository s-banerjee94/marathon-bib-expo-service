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
@Schema(description = "User counts block of the platform dashboard — always all-time")
public class PlatformUsersDto {

    @Schema(description = "Total users across the platform", example = "1284")
    private long total;

    @Schema(description = "User counts grouped by role (full UserRole key set)")
    private Map<String, Long> byRole;
}
