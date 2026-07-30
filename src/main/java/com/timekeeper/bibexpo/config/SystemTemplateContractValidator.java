package com.timekeeper.bibexpo.config;

import com.timekeeper.bibexpo.invitation.model.InviteMessageContext;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateParser;
import com.timekeeper.bibexpo.passwordreset.model.PasswordResetMessageContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start when a {@link SystemTemplatePurpose} declares a placeholder its flow cannot
 * actually render.
 *
 * <p>Each purpose declares its variable names as data so the messaging layer needs no dependency on
 * the flows that own the context objects, but that leaves the names and the getters they stand for as
 * two things that must agree. Rendering resolves a placeholder reflectively, so a field renamed on a
 * context object still satisfies template validation and then renders as an empty string — an invite
 * sent with no link in it, reported as delivered. This check closes the gap from above, where
 * importing both sides is free, and turns that silent failure into a failed deployment.
 */
@Component
@Slf4j
public class SystemTemplateContractValidator {

    @PostConstruct
    void verifyContracts() {
        List<String> problems = new ArrayList<>();
        for (SystemTemplatePurpose purpose : SystemTemplatePurpose.values()) {
            problems.addAll(problemsFor(purpose));
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("System message template variable contracts are out of step "
                    + "with the objects that render them: " + String.join("; ", problems));
        }
        log.info("Verified system message template variable contracts for {} purposes",
                SystemTemplatePurpose.values().length);
    }

    private List<String> problemsFor(SystemTemplatePurpose purpose) {
        List<String> problems = new ArrayList<>();
        if (!purpose.getVariables().contains(purpose.getRequiredVariable())) {
            problems.add(purpose + " requires #{" + purpose.getRequiredVariable()
                    + "}, which is not among its declared variables " + purpose.getVariables());
        }

        Class<?> context = contextOf(purpose);
        if (context == null) {
            log.info("{} has no sending flow yet; its variable contract stays unverified until one exists",
                    purpose);
            return problems;
        }

        purpose.getVariables().stream()
                .filter(name -> !MessageTemplateParser.resolvesAgainst(context, name))
                .sorted()
                .forEach(name -> problems.add(purpose + " declares #{" + name + "} but "
                        + context.getSimpleName() + " cannot supply it"));
        return problems;
    }

    /**
     * The object whose getters render this purpose's templates, or null for a purpose with no sending
     * flow yet. Deliberately a switch over every constant rather than a map: adding a purpose stops
     * compiling here until its context object is named.
     */
    private Class<?> contextOf(SystemTemplatePurpose purpose) {
        return switch (purpose) {
            case INVITE -> InviteMessageContext.class;
            case PASSWORD_RESET -> PasswordResetMessageContext.class;
            case OTP -> null;
        };
    }
}
