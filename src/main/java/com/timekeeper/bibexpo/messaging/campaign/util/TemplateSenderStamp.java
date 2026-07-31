package com.timekeeper.bibexpo.messaging.campaign.util;

import com.timekeeper.bibexpo.messaging.provider.model.enums.ProviderSource;
import com.timekeeper.bibexpo.messaging.provider.model.enums.TemplateMode;
import com.timekeeper.bibexpo.messaging.provider.service.CampaignProviderResolver;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Records which sender a template was written against, so a later change of sender is visible instead
 * of silent.
 *
 * <p>Two things are stamped. The rendering, so the editor keeps the shape the template was authored in.
 * And the sender's source, because a registered template id belongs to one vendor account: an
 * organization's DLT id means nothing in the platform's account, so a template that quietly falls back
 * to the platform sender would go out under the wrong registration and be billed to the wrong party.
 */
@Component
@RequiredArgsConstructor
public class TemplateSenderStamp {

    private final CampaignProviderResolver campaignProviderResolver;

    /**
     * What a template carries about the sender it was built for. {@code providerSource} is null when no
     * sender is configured yet, which leaves the pairing unchecked rather than wrongly enforced.
     */
    public record Stamp(TemplateMode renderMode, ProviderSource providerSource) {
    }

    /**
     * Resolves the stamp for a template being created.
     *
     * @param channel        channel the template belongs to
     * @param organizationId owning organization, or null to consider only the platform default
     * @param fallbackMode   rendering to record when no sender is configured yet
     * @return the rendering and sender source to persist
     */
    public Stamp resolveForSave(MessageChannel channel, Long organizationId, TemplateMode fallbackMode) {
        return campaignProviderResolver.resolveOptional(channel, organizationId)
                .map(resolved -> new Stamp(
                        Objects.requireNonNullElse(resolved.provider().getTemplateMode(), fallbackMode),
                        resolved.source()))
                .orElseGet(() -> new Stamp(fallbackMode, null));
    }

    /**
     * The mode an SMS template's content implies: variables without message text can only be
     * provider-rendered, anything else is client-rendered. Used as the creation fallback and to read
     * rows saved before the mode was stamped.
     *
     * @param bodyText      the template's message text
     * @param bodyVariables the template's newline-joined variable expressions
     * @return the implied mode
     */
    public static TemplateMode fromSmsContent(String bodyText, String bodyVariables) {
        boolean hasBody = bodyText != null && !bodyText.isBlank();
        boolean hasVariables = bodyVariables != null && !bodyVariables.isBlank();
        return hasVariables && !hasBody ? TemplateMode.PROVIDER_RENDERED : TemplateMode.CLIENT_RENDERED;
    }
}
