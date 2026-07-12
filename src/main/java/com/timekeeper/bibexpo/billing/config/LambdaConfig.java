package com.timekeeper.bibexpo.billing.config;

import com.timekeeper.bibexpo.config.AwsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaClientBuilder;

import java.net.URI;

/**
 * AWS Lambda client used to invoke the billing Lambda directly for an on-demand
 * (manual) bill. Mirrors {@link com.timekeeper.bibexpo.config.DynamoDBConfig}: an endpoint
 * override (LocalStack) uses the static test credentials; otherwise it targets real AWS via
 * the default credentials provider (EC2 instance role).
 */
@Configuration
@RequiredArgsConstructor
public class LambdaConfig {

    private final BillingProperties billingProperties;
    private final AwsProperties awsProperties;

    @Bean
    public LambdaClient lambdaClient() {
        String endpoint = billingProperties.getLambda().getEndpoint();
        LambdaClientBuilder builder = LambdaClient.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(awsProperties.credentialsProvider(endpoint));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
