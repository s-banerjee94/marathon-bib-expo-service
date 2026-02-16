package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.entity.Category;
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

    @Schema(description = "Category description", example = "Male participants aged 18 to 34 years")
    private String description;

    @Schema(description = "Race ID this category belongs to", example = "1")
    private Long raceId;

    @Schema(description = "Event ID (for context)", example = "1")
    private Long eventId;

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
                .description(category.getDescription())
                .raceId(category.getRace() != null ? category.getRace().getId() : null)
                .eventId(category.getRace() != null && category.getRace().getEvent() != null ?
                        category.getRace().getEvent().getId() : null)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .createdBy(category.getCreatedBy())
                .lastModifiedBy(category.getLastModifiedBy())
                .build();
    }
}
