package com.timekeeper.bibexpo.ai.agent.service.impl;

import com.timekeeper.bibexpo.ai.agent.config.AiAgentProperties;
import com.timekeeper.bibexpo.ai.agent.dto.response.AgentUsageResponse;
import com.timekeeper.bibexpo.ai.agent.exception.AiUsageLimitException;
import com.timekeeper.bibexpo.ai.agent.service.AiUsageService;
import com.timekeeper.bibexpo.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiUsageServiceImpl implements AiUsageService {

    // The daily window resets at UTC midnight, matching the rest of the system's UTC storage. Spring
    // (read) and the Python agent (write) must derive the same DAY# bucket, so both use UTC.
    private static final ZoneOffset BUDGET_ZONE = ZoneOffset.UTC;

    private static final String OVER_LIMIT_MESSAGE =
            "You've reached your AI assistant limit for today, please try again tomorrow.";

    private final DynamoDbClient dynamoDbClient;
    private final AiAgentProperties properties;

    @Override
    public void checkAllowed(User user) {
        Long limit = properties.getLimits().get(user.getRole());
        // A missing limit (misconfiguration) or an explicit negative value means "do not enforce".
        if (limit == null) {
            log.warn("No AI token limit configured for role {}; allowing without enforcement", user.getRole());
            return;
        }
        if (limit < 0) {
            return;
        }
        long used = usedTokensToday(user.getId());
        if (used >= limit) {
            log.info("AI usage blocked: user={} used={} limit={}", user.getId(), used, limit);
            throw new AiUsageLimitException(OVER_LIMIT_MESSAGE);
        }
    }

    @Override
    public AgentUsageResponse getUsage(User user) {
        Long limit = properties.getLimits().get(user.getRole());
        boolean capped = limit != null && limit >= 0;
        long used = usedTokensToday(user.getId());
        Instant resetsAt = LocalDate.now(BUDGET_ZONE).plusDays(1).atStartOfDay(BUDGET_ZONE).toInstant();
        return AgentUsageResponse.builder()
                .used(used)
                .limit(capped ? limit : -1L)
                .remaining(capped ? Math.max(0L, limit - used) : -1L)
                .resetsAt(resetsAt)
                .build();
    }

    private long usedTokensToday(Long userId) {
        String pk = "USER#" + userId;
        String sk = "DAY#" + LocalDate.now(BUDGET_ZONE);
        try {
            GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(properties.getUsageTable())
                    .key(Map.of(
                            "PK", AttributeValue.fromS(pk),
                            "SK", AttributeValue.fromS(sk)))
                    .build());
            AttributeValue tokens = response.item().get("tokens");
            return tokens == null ? 0L : Long.parseLong(tokens.n());
        } catch (Exception e) {
            // Never let a usage-store hiccup take down the assistant: fail open and log.
            log.warn("Could not read AI usage for user {}: {}", userId, e.toString());
            return 0L;
        }
    }
}
