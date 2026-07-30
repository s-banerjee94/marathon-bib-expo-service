package com.timekeeper.bibexpo.messaging.shared.enums;

import java.util.Set;

/**
 * System-initiated message flows that use an app-default template. Only INVITE is wired into a
 * sending flow now; PASSWORD_RESET and OTP have seeded templates and are the planned next consumers
 * of the same registry. BILL/GENERAL can be added later without structural change.
 *
 * <p>Each constant also declares the {@code #{...}} variables its flow supplies, and the one the
 * message is useless without (the link or code it exists to deliver). These names must stay in step
 * with the getters on the flow's context object — {@code InviteMessageContext},
 * {@code PasswordResetMessageContext} — since that object is what renders the template.
 */
public enum SystemTemplatePurpose {

    INVITE(Set.of("role", "organizationName", "inviteUrl"), "inviteUrl"),
    PASSWORD_RESET(Set.of("userName", "resetUrl"), "resetUrl"),
    OTP(Set.of("otp", "expiryMinutes"), "otp");

    private final Set<String> variables;
    private final String requiredVariable;

    SystemTemplatePurpose(Set<String> variables, String requiredVariable) {
        this.variables = variables;
        this.requiredVariable = requiredVariable;
    }

    /** Every {@code #{name}} this flow can resolve; anything else renders as an empty string. */
    public Set<String> getVariables() {
        return variables;
    }

    /** The variable an enabled template must reference, or the message carries no link/code. */
    public String getRequiredVariable() {
        return requiredVariable;
    }
}
