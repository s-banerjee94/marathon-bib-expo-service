package com.timekeeper.bibexpo.ai.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Dedicated {@link RestClient} for the Python AI agent, kept beside the agent properties so all
 * agent communication lives under {@code ai.agent}.
 */
@Configuration
public class AiAgentRestClientConfig {

    /**
     * Client for the Python AI agent, with explicit connect/read timeouts so a down or stalled agent
     * cannot hang a request indefinitely. Kept separate from the shared application {@code RestClient}
     * so its generous read timeout (a chat turn can take a while) is not imposed on other callers.
     *
     * <p>Uses the JDK {@link HttpClient} factory rather than {@code SimpleClientHttpRequestFactory}
     * because the agent's reset endpoint is a body-carrying {@code DELETE}, which the older
     * {@code HttpURLConnection}-based factory rejects ("HTTP method DELETE doesn't support output").
     */
    @Bean
    public RestClient aiAgentRestClient(AiAgentProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder().requestFactory(factory).build();
    }
}
