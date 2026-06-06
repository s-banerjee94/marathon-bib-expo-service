package com.timekeeper.bibexpo.billing.config;

import com.timekeeper.bibexpo.config.DynamoDBConfig;
import com.timekeeper.bibexpo.config.S3Config;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaClientBuilder;

import java.net.URI;

/**
 * AWS Lambda client used to invoke the billing Lambda directly for an on-demand
 * (manual) bill. Mirrors {@link S3Config}/{@link DynamoDBConfig}: an endpoint
 * override (LocalStack) uses static test credentials; otherwise it targets real
 * AWS via the default credentials provider (EC2 instance role).
 */
@Configuration
@RequiredArgsConstructor
public class LambdaConfig {

    private final BillingProperties billingProperties;

    @Value("${aws.region:us-east-1}")
    private String region;

    @Value("${aws.access-key-id:test}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:test}")
    private String secretAccessKey;

    @Bean
    public LambdaClient lambdaClient() {
        LambdaClientBuilder builder = LambdaClient.builder().region(Region.of(region));
        String endpoint = billingProperties.getLambda().getEndpoint();
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
