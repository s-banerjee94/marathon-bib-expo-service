package com.timekeeper.bibexpo.model.dto.request;

import com.timekeeper.bibexpo.model.entity.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
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
    @Schema(description = "Category name", example = "Men 18-34")
    private String categoryName;

    @Min(value = 0, message = "Minimum age must be at least 0")
    @Schema(description = "Minimum age for this category", example = "18")
    private Integer minAge;

    @Min(value = 0, message = "Maximum age must be at least 0")
    @Schema(description = "Maximum age for this category", example = "34")
    private Integer maxAge;

    @Schema(description = "Gender for this category", example = "MALE", allowableValues = {"MALE", "FEMALE", "OTHER", "OPEN"})
    private Gender gender;

    @AssertTrue(message = "Minimum age must be less than or equal to maximum age")
    public boolean isAgeRangeValid() {
        if (minAge == null || maxAge == null) {
            return true;
        }
        return minAge <= maxAge;
    }
}
