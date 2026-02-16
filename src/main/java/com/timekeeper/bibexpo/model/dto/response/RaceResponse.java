package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.entity.Race;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Race response payload")
public class RaceResponse {

    @Schema(description = "Race ID", example = "1")
    private Long id;

    @Schema(description = "Race name", example = "Full Marathon")
    private String raceName;

    @Schema(description = "Race description", example = "42.195 km race for experienced runners")
    private String raceDescription;

    @Schema(description = "Event ID this race belongs to", example = "1")
    private Long eventId;

    @Schema(description = "Number of categories in this race", example = "3")
    private Integer categoryCount;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @Schema(description = "Created by username", example = "admin")
    private String createdBy;

    @Schema(description = "Last modified by username", example = "admin")
    private String lastModifiedBy;

    public static RaceResponse fromEntity(Race race) {
        return RaceResponse.builder()
                .id(race.getId())
                .raceName(race.getRaceName())
                .raceDescription(race.getRaceDescription())
                .eventId(race.getEvent() != null ? race.getEvent().getId() : null)
                .categoryCount(race.getCategories() != null ? race.getCategories().size() : 0)
                .createdAt(race.getCreatedAt())
                .updatedAt(race.getUpdatedAt())
                .createdBy(race.getCreatedBy())
                .lastModifiedBy(race.getLastModifiedBy())
                .build();
    }
}
