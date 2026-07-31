package com.timekeeper.bibexpo.messaging.campaign.model.entity;

import com.timekeeper.bibexpo.messaging.provider.model.enums.ProviderSource;
import com.timekeeper.bibexpo.messaging.provider.model.enums.TemplateMode;

/**
 * Channel-agnostic view of a message template entity. {@link SmsTemplate} and
 * {@link WhatsAppTemplate} satisfy it through their Lombok-generated accessors, letting the
 * shared template service base class run the event-scoped lookup/delete/list flows without
 * knowing the channel.
 */
public interface TemplateEntity {

    /**
     * Database identifier of the template.
     */
    Long getId();

    /**
     * Human-readable template name.
     */
    String getName();

    /**
     * Which rendering the template was authored for, stamped from the organization's provider at
     * creation. Read on edit so the form keeps the shape it was written in even after the
     * organization switches provider; null on rows saved before the stamp existed.
     */
    TemplateMode getRenderMode();

    void setRenderMode(TemplateMode renderMode);

    /**
     * Which sender the template was written against — the organization's own or the platform default.
     * A registered template id belongs to one vendor account, so a send that resolves to the other
     * source must be refused rather than quietly delivered under the wrong registration. Null when no
     * sender was configured at the time of writing.
     */
    ProviderSource getProviderSource();

    void setProviderSource(ProviderSource providerSource);
}
