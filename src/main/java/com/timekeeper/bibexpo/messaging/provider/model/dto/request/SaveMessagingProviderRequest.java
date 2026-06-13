package com.timekeeper.bibexpo.messaging.provider.model.dto.request;

import com.timekeeper.bibexpo.messaging.provider.model.ProviderParam;
import com.timekeeper.bibexpo.messaging.provider.model.enums.AuthType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.HttpMethodType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.MessageContentType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.TemplateMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Root request to create or replace a channel's provider configuration. The channel comes from the
 * path. Secret fields ({@code authToken}, {@code password}) are write-only: leave them blank on an
 * update to keep the stored value (the response never returns the secret to copy back).
 */
@Data
@Schema(description = "Provider connection configuration for one channel")
public class SaveMessagingProviderRequest {

    @Schema(description = "Provider send endpoint", example = "https://www.fast2sms.com/dev/bulkV2")
    private String baseUrl;

    @NotNull
    @Schema(description = "HTTP verb the endpoint expects", example = "POST")
    private HttpMethodType httpMethod;

    @NotNull
    @Schema(description = "Authentication scheme; exactly one of token or username+password applies", example = "TOKEN")
    private AuthType authType;

    @Schema(description = "API key/token; required for TOKEN auth. Blank on update keeps the stored value", example = "your-api-key")
    private String authToken;

    @Schema(description = "Username; required for USERNAME_PASSWORD auth", example = "acme")
    private String username;

    @Schema(description = "Password; required for USERNAME_PASSWORD auth. Blank on update keeps the stored value", example = "secret")
    private String password;

    @NotNull
    @Schema(description = "Whether the message text is rendered by us (CLIENT_RENDERED) or the provider (PROVIDER_RENDERED)", example = "CLIENT_RENDERED")
    private TemplateMode templateMode;

    @Schema(description = "Body encoding for POST — JSON object or form-urlencoded; defaults to JSON when omitted", example = "JSON")
    private MessageContentType contentType;

    @Schema(description = "Header and query fields; each value may contain {{TOKEN}} placeholders")
    private List<ProviderParam> requestParams;

    @Schema(description = "POST body template; may contain {{TOKEN}} placeholders. Leave blank for GET. "
            + "In a FORM body, write any literal '+' as %2B (a raw '+' is decoded as a space)",
            example = "{ \"route\": \"q\", \"message\": \"{{MESSAGE}}\", \"numbers\": \"{{RECIPIENT}}\" }")
    private String bodyTemplate;

    @Schema(description = "Whether this provider is active for sending", example = "true")
    private boolean enabled;
}
