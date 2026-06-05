package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.S3Properties;
import com.timekeeper.bibexpo.exception.InvalidFileException;
import com.timekeeper.bibexpo.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.model.enums.UploadCategory;
import com.timekeeper.bibexpo.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService implements StorageService {

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    @Override
    public PresignUploadResponse createUploadUrl(UploadCategory category, long ownerId, String contentType) {
        String normalized = contentType == null ? null : contentType.trim().toLowerCase();
        if (normalized == null || !category.getAllowedContentTypes().contains(normalized)) {
            throw new InvalidFileException("This file type is not supported.");
        }

        String key = category.keyPrefix(ownerId) + "/" + UUID.randomUUID() + "." + extensionFor(normalized);
        long expiry = s3Properties.getUploadUrlExpirySeconds();

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .contentType(normalized)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiry))
                .putObjectRequest(objectRequest)
                .build();

        String url = s3Presigner.presignPutObject(presignRequest).url().toString();
        return PresignUploadResponse.builder()
                .uploadUrl(url)
                .objectKey(key)
                .contentType(normalized)
                .expiresInSeconds(expiry)
                .build();
    }

    @Override
    public String createDownloadUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(s3Properties.getDownloadUrlExpirySeconds()))
                .getObjectRequest(objectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public boolean objectExists(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .build());
    }

    private String extensionFor(String contentType) {
        return EXTENSION_BY_CONTENT_TYPE.getOrDefault(contentType, "bin");
    }
}
