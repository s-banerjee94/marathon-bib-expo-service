package com.timekeeper.bibexpo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "aws.s3")
@Data
public class S3Properties {

    /** Bucket that holds all uploaded media (profile pictures, logos, future files). */
    private String bucket;

    /** Override endpoint for LocalStack; empty/blank targets real AWS S3. */
    private String endpoint;

    /** Validity of presigned upload (PUT) URLs in seconds. */
    private long uploadUrlExpirySeconds = 600;

    /** Validity of presigned download (GET) URLs in seconds. */
    private long downloadUrlExpirySeconds = 3600;
}
