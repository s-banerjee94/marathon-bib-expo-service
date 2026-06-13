package com.timekeeper.bibexpo.messaging.provider.model.dto.response;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.provider.model.ProviderParam;
import com.timekeeper.bibexpo.messaging.provider.model.enums.AuthType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.HttpMethodType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.MessageContentType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.TemplateMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Provider configuration as returned to the root user. Secrets are masked — only a short tail is
 * shown, never the full token or password.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Provider connection configuration for one channel")
public class MessagingProviderResponse {

    private MessageChannel channel;
    private String baseUrl;
    private HttpMethodType httpMethod;
    private AuthType authType;

    @Schema(description = "Masked API key/token; null when not set", example = "••••8U4z")
    private String authTokenMasked;

    private String username;

    @Schema(description = "Masked password; null when not set", example = "••••cret")
    private String passwordMasked;

    private TemplateMode templateMode;

    @Schema(description = "Body encoding for POST requests", example = "JSON")
    private MessageContentType contentType;

    @Schema(description = "Header and query fields; each value may contain {{TOKEN}} placeholders")
    private List<ProviderParam> requestParams;

    @Schema(description = "POST body template with {{TOKEN}} placeholders; null for GET providers")
    private String bodyTemplate;

    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
