package com.timekeeper.bibexpo.messaging.campaign.util;

import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateContext;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateParser;

import java.util.Arrays;
import java.util.List;

/**
 * Renders a campaign template's stored body variables (newline-joined placeholder
 * expressions) into the ordered values a provider-rendered gateway expects — entry n
 * becomes positional variable {@code {{VAR:n}}} (Twilio ContentVariables for WhatsApp,
 * DLT variables for SMS).
 */
public final class CampaignVariableRenderer {

    private CampaignVariableRenderer() {}

    /**
     * How many variables a template declares, without rendering them — what the compatibility check
     * compares against the number the provider's request reads.
     *
     * @param joinedBodyVariables newline-joined placeholder expressions, may be null
     * @return the declared variable count, zero when none
     */
    public static int count(String joinedBodyVariables) {
        if (joinedBodyVariables == null || joinedBodyVariables.isBlank()) {
            return 0;
        }
        return joinedBodyVariables.split("\n").length;
    }

    public static List<String> render(String joinedBodyVariables, MessageTemplateContext context) {
        if (joinedBodyVariables == null || joinedBodyVariables.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joinedBodyVariables.split("\n"))
                .map(expression -> MessageTemplateParser.parse(expression, context))
                .toList();
    }
}
