package com.timekeeper.bibexpo.ai.agent.service.impl;

import com.timekeeper.bibexpo.ai.agent.config.AiAgentProperties;
import com.timekeeper.bibexpo.ai.agent.dto.request.AgentDecision;
import com.timekeeper.bibexpo.ai.agent.exception.AiAgentBusyException;
import com.timekeeper.bibexpo.ai.agent.exception.AiAgentException;
import com.timekeeper.bibexpo.ai.agent.service.AiAgentClient;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
@Slf4j
public class AiAgentClientImpl implements AiAgentClient {

    private final RestClient restClient;
    private final JwtService jwtService;
    private final AiAgentProperties properties;

    public AiAgentClientImpl(@Qualifier("aiAgentRestClient") RestClient restClient,
                             JwtService jwtService,
                             AiAgentProperties properties) {
        this.restClient = restClient;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Override
    public JsonNode chat(User user, String message, String mode) {
        Map<String, Object> body = baseBody(user);
        body.put("message", message);
        putMode(body, mode);
        return post("/chat", body);
    }

    @Override
    public JsonNode resume(User user, List<AgentDecision> decisions, String mode) {
        Map<String, Object> body = baseBody(user);
        body.put("decisions", decisions);
        putMode(body, mode);
        return post("/chat/resume", body);
    }

    @Override
    public void resetConversation(User user) {
        delete("/chat/memory", baseBody(user));
    }

    @Override
    public JsonNode history(User user, Integer cursor) {
        Map<String, Object> body = baseBody(user);
        if (cursor != null) {
            body.put("cursor", cursor);
        }
        return post("/chat/history", body);
    }

    // Identity Spring vouches for, plus a fresh per-call MCP token. The agent reads userId/role
    // from this body (Spring is the trusted minter); the token authenticates the agent to the MCP server.
    private Map<String, Object> baseBody(User user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", jwtService.generateAgentToken(user));
        body.put("userId", user.getId());
        body.put("role", user.getRole().name());
        if (user.getOrganization() != null) {
            body.put("organizationId", user.getOrganization().getId());
        }
        body.put("internalSecret", properties.getInternalSecret());
        return body;
    }

    // Approval mode is optional on the wire; only forward it when the caller supplied one.
    private void putMode(Map<String, Object> body, String mode) {
        if (mode != null && !mode.isBlank()) {
            body.put("mode", mode);
        }
    }

    private JsonNode post(String path, Map<String, Object> body) {
        return send(path, () -> restClient.post()
                .uri(properties.getBaseUrl() + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
    }

    // A body-carrying DELETE (the identity travels in the body, like the other calls); the agent
    // replies 204 No Content, so we discard the body instead of parsing it.
    private void delete(String path, Map<String, Object> body) {
        send(path, () -> restClient.method(HttpMethod.DELETE)
                .uri(properties.getBaseUrl() + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    // Every call to the agent fails the same way: log the cause and surface one friendly error, so
    // the controller can return 503 without leaking transport details to the client.
    private <T> T send(String path, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            log.warn("AI agent {} returned {}: {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            // The daily-budget 429 is enforced before we ever call the agent, so a 429 from the agent
            // means the upstream model is rate-limited: surface it as "busy", not a generic outage.
            if (e.getStatusCode().value() == 429) {
                throw new AiAgentBusyException("The AI assistant is busy right now, please try again in a few moments.");
            }
            throw new AiAgentException("The AI assistant is unavailable right now.");
        } catch (Exception e) {
            log.warn("AI agent {} call could not be completed: {}", path, e.toString());
            throw new AiAgentException("The AI assistant is unavailable right now.");
        }
    }
}
