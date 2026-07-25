package com.timekeeper.bibexpo.messaging.provider.model.dto.response;

import com.timekeeper.bibexpo.messaging.provider.model.enums.MessageContentType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.ProviderSource;
import com.timekeeper.bibexpo.messaging.provider.model.enums.TemplateMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Secret-free rendering hints about the campaign provider that will actually send for an organization
 * on a channel — the override-else-default resolution the send path uses. Lets the campaign template
 * editor pick the right authoring UI (message text vs positional variables) without exposing the
 * provider's connection details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Rendering hints for the effective campaign provider on a channel")
public class CampaignProviderStyleResponse {

    @Schema(description = "Whether an enabled campaign provider exists for the channel", example = "true")
    private boolean hasProvider;

    @Schema(description = "Which provider applies: the organization's own override or the platform default; null when none exists",
            example = "DEFAULT")
    private ProviderSource source;

    @Schema(description = "Rendering mode: CLIENT_RENDERED expects message text, PROVIDER_RENDERED expects positional variables; null when no provider",
            example = "CLIENT_RENDERED")
    private TemplateMode templateMode;

    @Schema(description = "POST body encoding of the effective provider; null when no provider", example = "JSON")
    private MessageContentType contentType;
}
