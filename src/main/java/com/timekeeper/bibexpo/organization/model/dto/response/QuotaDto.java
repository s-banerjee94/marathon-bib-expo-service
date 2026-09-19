package com.timekeeper.bibexpo.organization.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One cap and, where the platform counts it for the whole organization, how much of it is in use")
public class QuotaDto {

    @Schema(description = "Maximum number allowed", example = "30")
    private Integer max;

    @Schema(description = "Number currently in use. Always null for a cap that applies to a single item "
            + "or a single attribute rather than to the whole organization.",
            example = "12", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer used;

    static QuotaDto of(Integer max, Integer used) {
        return QuotaDto.builder().max(max).used(used).build();
    }

    static QuotaDto capOnly(Integer max) {
        return QuotaDto.builder().max(max).build();
    }
}
