package com.timekeeper.bibexpo.messaging.provider.model.dto.request;

import com.timekeeper.bibexpo.messaging.provider.model.ProviderParam;
import com.timekeeper.bibexpo.messaging.provider.model.enums.AuthType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.HttpMethodType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.MessageContentType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.TemplateMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
            + "In a FORM body, write any literal '+' as %2B (a raw '+' is decoded as a space). "
            + "The tokens must match templateMode: CLIENT_RENDERED has to use {{MESSAGE}}, PROVIDER_RENDERED has to "
            + "use {{VAR:n}} or {{VARIABLES_JSON}}, and a provider that disagrees with its own mode is rejected. "
            + "{{VAR:n}} counts from zero while {{VARIABLES_JSON}} emits keys from one, so {{VAR:0}} and key \"1\" "
            + "are the same value",
            example = "{ \"route\": \"q\", \"message\": \"{{MESSAGE}}\", \"numbers\": \"{{RECIPIENT}}\" }")
    private String bodyTemplate;

    @Size(max = 6, message = "Country code must not exceed 6 characters")
    @Pattern(regexp = "^\\+?[0-9]{1,5}$", message = "Country code must be digits, optionally prefixed with +")
    @Schema(description = "Country calling code prefixed by {{RECIPIENT_E164}} when a number is not already "
            + "international. Defaults to 91 when omitted", example = "91",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String defaultCountryCode;

    @Size(max = 200, message = "Success marker must not exceed 200 characters")
    @Schema(description = "Text the provider's response must contain for the send to count as successful. "
            + "Set it when the gateway answers HTTP 200 even for failures, otherwise leave blank and only the "
            + "status code decides", example = "\"type\":\"success\"", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String successContains;

    @Schema(description = "Whether this provider is active for sending", example = "true")
    private boolean enabled;
}
