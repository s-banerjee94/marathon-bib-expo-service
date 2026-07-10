package com.timekeeper.bibexpo.messaging.campaign.model.entity;

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
}
