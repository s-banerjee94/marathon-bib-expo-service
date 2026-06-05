package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.model.enums.UploadCategory;

/**
 * Object storage abstraction over S3 for uploaded media (profile pictures, logos,
 * and future files). The bucket is private: clients upload via short-lived presigned
 * PUT URLs and read via short-lived presigned GET URLs. The stored entity holds only
 * the object key; a fresh download URL is presigned on every read.
 */
public interface StorageService {

    /**
     * Generate a presigned PUT URL for a new object under the given category and owner.
     * The server picks the object key (clients cannot choose arbitrary paths). The
     * client must upload with the same Content-Type returned here.
     *
     * @param category    logical kind of upload (decides key prefix and allowed types)
     * @param ownerId     id of the owning entity (user, organization, or event)
     * @param contentType MIME type of the file to upload
     * @return the presigned upload URL plus the object key to attach afterwards
     * @throws com.timekeeper.bibexpo.exception.InvalidFileException if the content type is not allowed for the category
     */
    PresignUploadResponse createUploadUrl(UploadCategory category, long ownerId, String contentType);

    /**
     * Generate a short-lived presigned GET URL for an existing object, or null if the
     * key is null/blank.
     *
     * @param objectKey stored S3 object key
     * @return a presigned download URL, or null when objectKey is absent
     */
    String createDownloadUrl(String objectKey);

    /**
     * Whether an object actually exists in the bucket. Used to confirm a presigned
     * upload completed before attaching the key to an entity.
     *
     * @param objectKey stored S3 object key
     * @return true if the object exists, false otherwise
     */
    boolean objectExists(String objectKey);

    /**
     * Delete an object. No-op for a null/blank key; succeeds even if the object is
     * already absent.
     *
     * @param objectKey stored S3 object key
     */
    void delete(String objectKey);
}
