package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.entity.Category;
import com.timekeeper.bibexpo.model.entity.Gender;
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
@Schema(description = "Category response payload")
public class CategoryResponse {

    @Schema(description = "Category ID", example = "1")
    private Long id;

    @Schema(description = "Category name", example = "Men 18-34")
    private String categoryName;

    @Schema(description = "Minimum age", example = "18")
    private Integer minAge;

    @Schema(description = "Maximum age", example = "34")
    private Integer maxAge;

    @Schema(description = "Gender", example = "MALE")
    private Gender gender;

    @Schema(description = "Race ID this category belongs to", example = "1")
    private Long raceId;

    @Schema(description = "Event ID (for context)", example = "1")
    private Long eventId;

    @Schema(description = "Category deleted status", example = "false")
    private Boolean deleted;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @Schema(description = "Created by username", example = "admin")
    private String createdBy;

    @Schema(description = "Last modified by username", example = "admin")
    private String lastModifiedBy;

    public static CategoryResponse fromEntity(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .categoryName(category.getCategoryName())
                .minAge(category.getMinAge())
                .maxAge(category.getMaxAge())
                .gender(category.getGender())
                .raceId(category.getRace() != null ? category.getRace().getId() : null)
                .eventId(category.getRace() != null && category.getRace().getEvent() != null ?
                        category.getRace().getEvent().getId() : null)
                .deleted(category.getDeleted())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .createdBy(category.getCreatedBy())
                .lastModifiedBy(category.getLastModifiedBy())
                .build();
    }
}
