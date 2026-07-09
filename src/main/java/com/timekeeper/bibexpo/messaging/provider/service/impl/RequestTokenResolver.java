package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        FORM
    }

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Z0-9_]+)(?::(\\d+))?\\s*}}");

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
            case "RECIPIENT_E164" -> toE164(message.getRecipientPhone());
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

    /** Prepends the India country code unless the number is already in international (+) format. */
    private String toE164(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String trimmed = phone.trim();
        return trimmed.startsWith("+") ? trimmed : "+91" + trimmed;
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
