package com.timekeeper.bibexpo.ai.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Supplies the AI assistant's system prompt from an external resource so it can be tuned without
 * changing or rebuilding code. The location is set by {@code app.ai.system-prompt-location} and may
 * be a bundled classpath resource (the default) or a {@code file:} path for live editing.
 *
 * <p>The prompt is read fresh on every turn, so edits to a {@code file:} location take effect on the
 * next message. If a read fails, the last value that loaded successfully is reused.
 */
@Component
@Slf4j
public class SystemPromptProvider {

    private final Resource resource;
    private volatile String lastGood;

    public SystemPromptProvider(ResourceLoader resourceLoader,
                                @Value("${app.ai.system-prompt-location:classpath:prompts/system-prompt.st}") String location) {
        this.resource = resourceLoader.getResource(location);
    }

    /**
     * Read and return the current system prompt.
     *
     * @return the prompt text
     * @throws IllegalStateException if it cannot be read and nothing has loaded before
     */
    public String get() {
        try (InputStream in = resource.getInputStream()) {
            String text = StreamUtils.copyToString(in, StandardCharsets.UTF_8).strip();
            if (text.isEmpty()) {
                throw new IOException("system prompt resource is empty");
            }
            lastGood = text;
            return text;
        } catch (IOException e) {
            if (lastGood != null) {
                log.warn("Could not read system prompt from {}, reusing last loaded value: {}",
                        resource, e.getMessage());
                return lastGood;
            }
            throw new IllegalStateException("AI system prompt is not available at " + resource, e);
        }
    }
}
