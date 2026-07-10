package com.timekeeper.bibexpo.invitation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Variable context for invite messages. Its getters back the {@code #{role}},
 * {@code #{organizationName}} and {@code #{inviteUrl}} tokens used by the invite SMS/WhatsApp
 * templates (resolved by {@code MessageTemplateParser}).
 */
@Getter
@Builder
@AllArgsConstructor
@ToString
public class InviteMessageContext {

    private final String role;
    private final String organizationName;
    private final String inviteUrl;
}
