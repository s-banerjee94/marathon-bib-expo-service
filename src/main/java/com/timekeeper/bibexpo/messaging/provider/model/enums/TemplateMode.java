package com.timekeeper.bibexpo.messaging.provider.model.enums;

/**
 * Where the message text is assembled. {@code CLIENT_RENDERED}: we fill the placeholders and send
 * the finished text. {@code PROVIDER_RENDERED}: the provider holds the template, we send a template
 * id plus the variable values.
 */
public enum TemplateMode {
    CLIENT_RENDERED,
    PROVIDER_RENDERED
}
