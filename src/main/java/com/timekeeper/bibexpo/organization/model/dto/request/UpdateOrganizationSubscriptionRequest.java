package com.timekeeper.bibexpo.organization.model.dto.request;

import com.timekeeper.bibexpo.organization.model.enums.SubscriptionTier;
import com.timekeeper.bibexpo.shared.validation.ValidEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for changing an organization's plan: its subscription tier and limits (all fields optional)")
public class UpdateOrganizationSubscriptionRequest {

    @ValidEnum(enumClass = SubscriptionTier.class)
    @Schema(description = "Subscription tier; omit to keep it, send PAY_AS_YOU_GO to fall back to the baseline",
            example = "PREMIUM", allowableValues = {"PAY_AS_YOU_GO", "PREMIUM", "PARTNER"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String subscriptionTier;

    @Valid
    @Schema(description = "Per-role user quota caps. Optional; only the roles you include are changed.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private UserQuotaRequest userQuota;

    @Valid
    @Schema(description = "Inventory caps. Optional; only the caps you include are changed.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private InventoryQuotaRequest inventoryQuota;
}
