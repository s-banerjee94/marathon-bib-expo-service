package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.model.dto.response.UserResponse;
import com.timekeeper.bibexpo.security.CurrentActor;

/**
 * Profile-picture storage operations for user accounts: presigned upload, attach,
 * remove, and best-effort cleanup. Authorization mirrors user update permissions —
 * whoever may update a user may manage that user's picture.
 */
public interface UserProfileMediaService {

    /**
     * Create a presigned S3 upload URL for a user's profile picture. The caller must
     * have permission to update the target user (same rules as user update).
     *
     * @param userId      the user whose picture is being uploaded
     * @param contentType MIME type of the file (validated against allowed image types)
     * @param actor       the authenticated user making the request
     * @return the presigned upload URL plus the object key to attach afterwards
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException        if the user is not found
     * @throws com.timekeeper.bibexpo.exception.AccessForbiddenException  if the caller lacks permission
     * @throws com.timekeeper.bibexpo.exception.InvalidFileException         if the content type is not allowed
     */
    PresignUploadResponse createProfilePictureUploadUrl(Long userId, String contentType, CurrentActor actor);

    /**
     * Attach a previously uploaded object as the user's profile picture. Verifies the
     * key belongs to the user and that the object exists in S3, then replaces any
     * previous picture (the old object is deleted).
     *
     * @param userId    the user whose picture is being set
     * @param objectKey the object key returned by the presign step
     * @param actor     the authenticated user making the request
     * @return the updated user response (with a fresh presigned picture URL)
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException        if the user is not found
     * @throws com.timekeeper.bibexpo.exception.AccessForbiddenException  if the caller lacks permission
     * @throws com.timekeeper.bibexpo.exception.InvalidFileException         if the key is invalid or the object is missing
     */
    UserResponse attachProfilePicture(Long userId, String objectKey, CurrentActor actor);

    /**
     * Remove the user's profile picture, deleting the object from S3.
     *
     * @param userId the user whose picture is being removed
     * @param actor  the authenticated user making the request
     * @return the updated user response
     * @throws com.timekeeper.bibexpo.exception.UserNotFoundException        if the user is not found
     * @throws com.timekeeper.bibexpo.exception.AccessForbiddenException  if the caller lacks permission
     */
    UserResponse removeProfilePicture(Long userId, CurrentActor actor);

    /**
     * Best-effort deletion of a stored picture object: orphaned objects are tolerable,
     * a failed cleanup must never roll back the surrounding account change. Used by
     * account lifecycle operations (archive, organization purge) and picture replacement.
     *
     * @param objectKey the object key to delete; null is ignored
     */
    void deletePictureQuietly(String objectKey);
}
