package com.timekeeper.bibexpo.messaging.campaign.util;

import com.timekeeper.bibexpo.messaging.provider.model.enums.ProviderSource;
import com.timekeeper.bibexpo.messaging.provider.service.CampaignProviderResolver;
import com.timekeeper.bibexpo.messaging.provider.service.impl.ProviderMappingValidator;
import com.timekeeper.bibexpo.messaging.provider.service.impl.ProviderMappingValidator.TemplateContent;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/**
 * Checks a template against the sender it would actually go through, before anything is dispatched.
 *
 * <p>Every message costs money, and the failure this catches is the expensive kind: a template that
 * cannot fill what the sender's request asks for still sends — the vendor charges for it, returns
 * success, and delivers a message with holes in it. So the check runs twice, at the two moments the
 * pairing can change: when a campaign is armed, where it can still be refused to the operator's face,
 * and again immediately before dispatch, because the organization may have switched sender in between.
 *
 * <p>An unconfigured channel is not treated as a failure — the resolver already decides whether that
 * is fatal, and locally it hands back a stub.
 */
@Component
@RequiredArgsConstructor
public class CampaignCompatibilityGuard {

    private final CampaignProviderResolver campaignProviderResolver;
    private final ProviderMappingValidator mappingValidator;

    /**
     * What stops this template from being sent over the channel, if anything.
     *
     * @param channel        channel the message would go over
     * @param organizationId organization whose sender applies, null for the platform default
     * @param content        what the template supplies
     * @return the problem, phrased for the operator, or empty when the pairing is sound
     */
    public Optional<String> problem(MessageChannel channel, Long organizationId, TemplateContent content,
                                    ProviderSource stampedSource) {
        return campaignProviderResolver.resolveOptional(channel, organizationId)
                .flatMap(resolved -> senderChanged(resolved.source(), stampedSource, channel)
                        .or(() -> mappingValidator.unmetRequirement(resolved.provider(), content)));
    }

    /**
     * Whether the sender in force is a different one from the sender the template was written against.
     * Checked before the mapping, because a template pointing at another vendor's account is wrong even
     * when the shapes happen to line up. Unstamped templates are left alone.
     */
    private Optional<String> senderChanged(ProviderSource inForce, ProviderSource stamped, MessageChannel channel) {
        if (stamped == null || stamped == inForce) {
            return Optional.empty();
        }
        return Optional.of(stamped == ProviderSource.ORGANIZATION
                ? "This template was built for your own " + channel + " sender, which is no longer in use — switch it "
                        + "back on, or rebuild the template for the platform sender."
                : "This template was built for the platform " + channel + " sender, but your own sender is now in "
                        + "use — rebuild the template for it.");
    }

    /**
     * Refuses the operation when the template cannot satisfy the sender.
     *
     * @param exceptionFactory builds the caller's own exception from the problem text
     */
    public void require(MessageChannel channel, Long organizationId, TemplateContent content,
                        ProviderSource stampedSource, Function<String, RuntimeException> exceptionFactory) {
        problem(channel, organizationId, content, stampedSource).ifPresent(problem -> {
            throw exceptionFactory.apply(problem);
        });
    }
}
