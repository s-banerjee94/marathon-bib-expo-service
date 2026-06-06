package com.timekeeper.bibexpo.billing.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.SchedulerClientBuilder;

import java.net.URI;

/**
 * EventBridge Scheduler client used to arm/cancel the deferred auto-bill timer.
 * Mirrors {@link S3Config}/{@link DynamoDBConfig}: an endpoint override (LocalStack)
 * uses static test credentials; otherwise it targets real AWS via the default
 * credentials provider (EC2 instance role).
 */
@Configuration
@RequiredArgsConstructor
public class SchedulerConfig {

    private final BillingProperties billingProperties;

    @Value("${aws.region:us-east-1}")
    private String region;

    @Value("${aws.access-key-id:test}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:test}")
    private String secretAccessKey;

    @Bean
    public SchedulerClient schedulerClient() {
        SchedulerClientBuilder builder = SchedulerClient.builder().region(Region.of(region));
        String endpoint = billingProperties.getScheduler().getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }
}
