package com.timekeeper.bibexpo.ai.agent.config;

import com.timekeeper.bibexpo.model.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.EnumMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "app.ai.agent")
@Validated
@Data
public class AiAgentProperties {

    /** Python agent base URL (server-to-server, e.g. http://localhost:8000). */
    private String baseUrl;

    /** Shared secret sent to the agent on every call; must match its BIBEXPO_INTERNAL_SECRET. Required. */
    @NotBlank
    private String internalSecret;

    /** Connect timeout (ms) for calls to the agent; fail fast if it is unreachable. */
    private long connectTimeoutMs = 5000;

    /**
     * Read timeout (ms) for calls to the agent. Generous because a single chat turn can run several
     * model calls and tool round-trips before replying; this only trips on a genuinely stalled agent.
     */
    private long readTimeoutMs = 120000;

    /** DynamoDB table holding per-user daily token-usage counters (shared with the Python agent). */
    private String usageTable;

    /**
     * Daily token budget per role (prompt + completion). A user is blocked with 429 once their day's
     * usage reaches this value. DISTRIBUTOR has no AI access, so it is intentionally absent.
     */
    private Map<UserRole, Long> limits = new EnumMap<>(UserRole.class);
}
