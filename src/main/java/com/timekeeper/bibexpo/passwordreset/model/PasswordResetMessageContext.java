package com.timekeeper.bibexpo.passwordreset.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Variable context for password-reset messages. Its getters back the {@code #{userName}} and
 * {@code #{resetUrl}} tokens used by the PASSWORD_RESET SMS/WhatsApp templates (resolved by
 * {@code SmsTemplateParser}).
 */
@Getter
@Builder
@AllArgsConstructor
@ToString
public class PasswordResetMessageContext {

    private final String userName;
    private final String resetUrl;
}
