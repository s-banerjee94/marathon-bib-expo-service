package com.timekeeper.bibexpo.organization.model.dto.response;

import com.timekeeper.bibexpo.organization.model.entity.OrganizationLimit;
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
    private QuotaDto admins;

    @Schema(description = "Organizer user quota")
    private QuotaDto organizerUsers;

    @Schema(description = "Distributor quota")
    private QuotaDto distributors;

    public static UserQuotaDto fromEntity(OrganizationLimit limit) {
        return UserQuotaDto.builder()
                .admins(QuotaDto.of(limit.getMaxAdmins(), limit.getUsedAdmins()))
                .organizerUsers(QuotaDto.of(limit.getMaxOrganizerUsers(), limit.getUsedOrganizerUsers()))
                .distributors(QuotaDto.of(limit.getMaxDistributors(), limit.getUsedDistributors()))
                .build();
    }
}
