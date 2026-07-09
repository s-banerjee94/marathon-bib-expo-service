package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Per-role user quota caps. Optional; default caps apply for any role omitted.")
public class UserQuotaRequest {

    @Valid
    @Schema(description = "Organizer administrator cap", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private RoleQuotaRequest admins;

    @Valid
    @Schema(description = "Organizer user cap", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private RoleQuotaRequest organizerUsers;

    @Valid
    @Schema(description = "Distributor cap", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private RoleQuotaRequest distributors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "A single role's maximum allowed")
    public static class RoleQuotaRequest {

        @Min(value = 1, message = "Maximum must be at least 1")
        @Schema(description = "Maximum number allowed", example = "3", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private Integer max;
    }
}
