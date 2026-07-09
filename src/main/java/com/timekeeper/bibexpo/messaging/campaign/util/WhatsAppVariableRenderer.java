package com.timekeeper.bibexpo.messaging.campaign.util;

import com.timekeeper.bibexpo.util.SmsTemplateContext;
import com.timekeeper.bibexpo.util.SmsTemplateParser;

import java.util.Arrays;
import java.util.List;

/**
 * Renders a WhatsApp template's stored body variables (newline-joined placeholder
 * expressions) into the ordered values for Twilio's ContentVariables — entry n becomes
 * template variable {{n}}.
 */
public final class WhatsAppVariableRenderer {

    private WhatsAppVariableRenderer() {}

    public static List<String> render(String joinedBodyVariables, SmsTemplateContext context) {
        if (joinedBodyVariables == null || joinedBodyVariables.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joinedBodyVariables.split("\n"))
                .map(expression -> SmsTemplateParser.parse(expression, context))
                .toList();
    }
}
