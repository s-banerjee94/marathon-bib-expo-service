package com.timekeeper.bibexpo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * Shared AWS region and credentials for every SDK client (DynamoDB, S3, Scheduler, Lambda).
 * Keys are only used against an endpoint override (LocalStack); with no endpoint the default
 * provider chain is used so EC2 picks up its instance role — no keys are stored on the box.
 */
@Configuration
@ConfigurationProperties(prefix = "aws")
@Data
public class AwsProperties {

    private String region = "us-east-1";
    private String accessKeyId = "test";
    private String secretAccessKey = "test";

    /**
     * Static credentials when an endpoint override (LocalStack) is set; otherwise the default
     * provider chain (EC2 instance role, env, etc.).
     *
     * @param endpoint the client's endpoint override, or null/blank to target real AWS
     * @return the credentials provider to attach to the SDK client builder
     */
    public AwsCredentialsProvider credentialsProvider(String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }
        return DefaultCredentialsProvider.create();
    }
}
