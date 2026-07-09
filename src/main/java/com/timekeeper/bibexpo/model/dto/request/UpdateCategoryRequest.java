package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for updating an existing category")
public class UpdateCategoryRequest {

    @Size(min = 2, max = 200, message = "Category name must be between 2 and 200 characters")
    @Schema(description = "Category name", example = "Men 18-34", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String categoryName;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Schema(description = "Category description", example = "Male participants aged 18 to 34 years", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String description;
}
