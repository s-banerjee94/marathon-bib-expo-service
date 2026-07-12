package com.timekeeper.bibexpo.billing.config;

import com.timekeeper.bibexpo.config.AwsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.SchedulerClientBuilder;

import java.net.URI;

/**
 * EventBridge Scheduler client used to arm/cancel the deferred auto-bill timer.
 * Mirrors {@link com.timekeeper.bibexpo.config.DynamoDBConfig}: an endpoint override
 * (LocalStack) uses the static test credentials; otherwise it targets real AWS via the
 * default credentials provider (EC2 instance role).
 */
@Configuration
@RequiredArgsConstructor
public class SchedulerConfig {

    private final BillingProperties billingProperties;
    private final AwsProperties awsProperties;

    @Bean
    public SchedulerClient schedulerClient() {
        String endpoint = billingProperties.getScheduler().getEndpoint();
        SchedulerClientBuilder builder = SchedulerClient.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(awsProperties.credentialsProvider(endpoint));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
