package com.timekeeper.bibexpo.organization.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of unlocking an organization's users")
public record MembersUnlockedResponse(
        @Schema(description = "Users unlocked: only those locked because the organization was disabled", example = "12")
        int unlocked) {
}
