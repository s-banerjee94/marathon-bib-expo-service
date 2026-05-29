package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.entity.OrganizationLimit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Per-role user quota: maximum allowed and current usage")
public class UserQuotaDto {

    @Schema(description = "Organizer administrator quota")
    private RoleQuota admins;

    @Schema(description = "Organizer user quota")
    private RoleQuota organizerUsers;

    @Schema(description = "Distributor quota")
    private RoleQuota distributors;

    public static UserQuotaDto fromEntity(OrganizationLimit limit) {
        return UserQuotaDto.builder()
                .admins(RoleQuota.of(limit.getMaxAdmins(), limit.getUsedAdmins()))
                .organizerUsers(RoleQuota.of(limit.getMaxOrganizerUsers(), limit.getUsedOrganizerUsers()))
                .distributors(RoleQuota.of(limit.getMaxDistributors(), limit.getUsedDistributors()))
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "A single role's maximum allowed and current usage")
    public static class RoleQuota {

        @Schema(description = "Maximum number allowed", example = "3")
        private Integer max;

        @Schema(description = "Number currently in use", example = "2")
        private Integer used;

        static RoleQuota of(Integer max, Integer used) {
            return RoleQuota.builder().max(max).used(used).build();
        }
    }
}
