package com.timekeeper.bibexpo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3 client and presigner wiring. When an endpoint override is set (LocalStack) it uses
 * path-style access and the static test credentials from {@link AwsProperties}; otherwise
 * it targets real AWS S3 via the default credentials provider (EC2 instance role).
 */
@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final S3Properties s3Properties;
    private final AwsProperties awsProperties;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(awsProperties.credentialsProvider(s3Properties.getEndpoint()));
        if (hasEndpointOverride()) {
            // The pinned LocalStack image predates the SDK's default CRC32 integrity checksums
            // (SDK >= 2.30), so keep the legacy opt-in behavior for local dev only.
            builder.endpointOverride(URI.create(s3Properties.getEndpoint()))
                    .forcePathStyle(true)
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(awsProperties.credentialsProvider(s3Properties.getEndpoint()));
        if (hasEndpointOverride()) {
            builder.endpointOverride(URI.create(s3Properties.getEndpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    private boolean hasEndpointOverride() {
        return s3Properties.getEndpoint() != null && !s3Properties.getEndpoint().isBlank();
    }
}
