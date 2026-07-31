package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Substitutes {@code {{TOKEN}}} placeholders in a provider's URL, header/query values, and body
 * template with values drawn from the provider credentials and the outbound message. Each substituted
 * value is escaped for its target context — raw, JSON string, or form-urlencoded — so a value can
 * never break the surrounding request. This is what makes a provider purely data: any HTTP shape is
 * expressed as tokens rather than code.
 */
@Component
class RequestTokenResolver {

    /** How a substituted value is escaped for the surrounding context. */
    enum Escape {
        NONE,
        JSON,
        FORM,
        XML
    }

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Z0-9_]+)(?::(\\d+))?\\s*}}");

    /**
     * Highest {@code {{VAR:n}}} index a piece of provider configuration reads, so a caller can tell
     * how many variables the mapping expects. Note the indexing asymmetry this inherits:
     * {@code {{VAR:n}}} is zero-based while {@code {{VARIABLES_JSON}}} emits one-based keys, so
     * {@code {{VAR:0}}} and the {@code "1"} key of the JSON map are the same value.
     *
     * @param template a configured URL, param value, or body template
     * @return the highest index found, or -1 when the template reads no positional variable
     */
    static int maxVariableIndex(String template) {
        if (template == null || template.isEmpty()) {
            return -1;
        }
        return TOKEN.matcher(template).results()
                .filter(result -> "VAR".equals(result.group(1)) && result.group(2) != null)
                .mapToInt(result -> Integer.parseInt(result.group(2)))
                .max()
                .orElse(-1);
    }

    /**
     * The token names a piece of provider configuration references, without the index — so
     * {@code {{VAR:0}}} reports as {@code VAR}. Lets a caller ask what a mapping actually reads
     * without restating the token syntax.
     *
     * @param template a configured URL, param value, or body template
     * @return the distinct token names found, empty when there are none
     */
    static Set<String> tokenNames(String template) {
        if (template == null || template.isEmpty()) {
            return Set.of();
        }
        return TOKEN.matcher(template).results()
                .map(result -> result.group(1))
                .collect(Collectors.toSet());
    }

    String resolve(String template, Escape escape, MessagingProvider provider, OutboundMessage message) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder(template.length());
        while (matcher.find()) {
            String raw = value(matcher.group(1), matcher.group(2), provider, message);
            matcher.appendReplacement(out, Matcher.quoteReplacement(applyEscape(raw, escape)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String value(String token, String index, MessagingProvider provider, OutboundMessage message) {
        return switch (token) {
            case "RECIPIENT" -> nullToEmpty(message.getRecipientPhone());
            case "RECIPIENT_E164" -> toE164(message.getRecipientPhone(), provider.getDefaultCountryCode());
            case "RECIPIENT_CC" -> withCountryCode(message.getRecipientPhone(), provider.getDefaultCountryCode());
            case "RECIPIENT_EMAIL" -> nullToEmpty(message.getRecipientEmail());
            case "SUBJECT" -> nullToEmpty(message.getSubject());
            case "MESSAGE" -> nullToEmpty(message.getMessage());
            case "TEMPLATE_ID" -> nullToEmpty(message.getTemplateId());
            case "SENDER_ID" -> nullToEmpty(message.getSenderId());
            case "VAR" -> variableAt(message, index);
            case "VARIABLES_JSON" -> variablesJson(message);
            case "API_KEY" -> nullToEmpty(provider.getAuthToken());
            case "USERNAME" -> nullToEmpty(provider.getUsername());
            case "PASSWORD" -> nullToEmpty(provider.getPassword());
            case "BASIC_AUTH" -> basicAuth(provider);
            default -> "";
        };
    }

    private String applyEscape(String value, Escape escape) {
        return switch (escape) {
            case NONE -> value;
            case JSON -> jsonEscape(value);
            case FORM -> URLEncoder.encode(value, StandardCharsets.UTF_8);
            case XML -> xmlEscape(value);
        };
    }

    private String variableAt(OutboundMessage message, String index) {
        List<String> variables = message.getVariables();
        if (variables == null || index == null) {
            return "";
        }
        int i = Integer.parseInt(index);
        return i >= 0 && i < variables.size() ? nullToEmpty(variables.get(i)) : "";
    }

    /** Builds {@code {"1":"v0","2":"v1",...}} from the ordered variables (e.g. Twilio ContentVariables). */
    private String variablesJson(OutboundMessage message) {
        List<String> variables = message.getVariables();
        if (variables == null || variables.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < variables.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(i + 1).append("\":\"")
                    .append(jsonEscape(nullToEmpty(variables.get(i)))).append('"');
        }
        return sb.append('}').toString();
    }

    private String basicAuth(MessagingProvider provider) {
        String credentials = nullToEmpty(provider.getUsername()) + ":" + nullToEmpty(provider.getPassword());
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Prepends the provider's country calling code unless the number is already international.
     * The code is per provider rather than a constant, so the same engine serves any market.
     */
    private String toE164(String phone, String countryCode) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String trimmed = phone.trim();
        if (trimmed.startsWith("+")) {
            return trimmed;
        }
        String code = countryCode == null || countryCode.isBlank() ? "" : countryCode.trim().replace("+", "");
        return "+" + code + trimmed;
    }

    /**
     * Country code and number with no leading plus, for gateways that reject the {@code +} form. Mirrors
     * {@link #toE164} otherwise: a number already in international form keeps its own code.
     */
    private String withCountryCode(String phone, String countryCode) {
        String e164 = toE164(phone, countryCode);
        return e164.startsWith("+") ? e164.substring(1) : e164;
    }

    private String xmlEscape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
