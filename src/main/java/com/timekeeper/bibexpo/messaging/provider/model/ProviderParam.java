package com.timekeeper.bibexpo.messaging.provider.model;

import com.timekeeper.bibexpo.messaging.provider.model.enums.ParamLocation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One header or query field the provider expects: its key, where it goes, and a value template.
 * The value may embed {@code {{TOKEN}}} placeholders (e.g. {@code {{API_KEY}}}, {@code {{RECIPIENT}}})
 * resolved at send time. The list is stored as JSON on {@code MessagingProvider}; the request body is
 * built separately from the provider's {@code bodyTemplate}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One header or query field: its key, where it goes, and a token value template")
public class ProviderParam {

    @Schema(description = "Key the provider expects", example = "authorization")
    private String name;

    @Schema(description = "Where the field is placed in the request", example = "HEADER")
    private ParamLocation location;

    @Schema(description = """
            Value template. May contain {{TOKEN}} placeholders resolved at send time: \
            {{RECIPIENT}}/{{RECIPIENT_E164}} (bare vs +91 phone), {{MESSAGE}} (rendered text), \
            {{TEMPLATE_ID}}, {{SENDER_ID}}, {{VAR:0}} (positional variable), {{VARIABLES_JSON}}, \
            {{API_KEY}}, {{USERNAME}}, {{PASSWORD}}, {{BASIC_AUTH}}. Literal text is sent as-is.""",
            example = "{{API_KEY}}")
    private String value;
}
