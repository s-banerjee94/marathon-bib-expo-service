package com.timekeeper.bibexpo.model.dto.response.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cumulative end-of-bucket values for each tracked platform metric, oldest first")
public class PlatformTrendSeriesDto {

    @Schema(description = "Total organization count at the end of each bucket")
    private List<Long> organizations;

    @Schema(description = "Total event count at the end of each bucket")
    private List<Long> events;

    @Schema(description = "Total user count at the end of each bucket")
    private List<Long> users;

    @Schema(description = "Active (PUBLISHED) event count at the end of each bucket")
    private List<Long> activeEvents;

    @Schema(description = "Distinct city count at the end of each bucket")
    private List<Long> cities;
}
