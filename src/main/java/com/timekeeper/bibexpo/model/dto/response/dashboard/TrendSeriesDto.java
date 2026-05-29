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
@Schema(description = "Cumulative end-of-bucket values for each tracked metric")
public class TrendSeriesDto {

    @Schema(description = "Total event count at the end of each bucket")
    private List<Long> events;

    @Schema(description = "Active (PUBLISHED) event count at the end of each bucket")
    private List<Long> active;

    @Schema(description = "Total user count at the end of each bucket")
    private List<Long> users;

    @Schema(description = "Distinct city count at the end of each bucket")
    private List<Long> cities;
}
