package com.timekeeper.bibexpo.model.enums;

import lombok.Getter;

import java.util.Set;

/**
 * Logical kinds of uploadable media. Each category owns its object-key layout, the
 * content types it accepts, and a size cap, so adding a new kind of upload (e.g. an
 * event document or org file) is a single enum entry rather than new plumbing.
 *
 * <p>Object keys are {@code {ownerRoot}/{ownerId}/{leaf}/{uuid}.{ext}} — for example
 * {@code organizations/42/logo/3f2c....png}.
 */
@Getter
public enum UploadCategory {

    PROFILE_PICTURE("users", "profile", Rules.IMAGE_TYPES, Rules.IMAGE_MAX_BYTES),
    ORGANIZATION_LOGO("organizations", "logo", Rules.IMAGE_TYPES, Rules.IMAGE_MAX_BYTES),
    EVENT_LOGO("events", "logo", Rules.IMAGE_TYPES, Rules.IMAGE_MAX_BYTES);

    private final String ownerRoot;
    private final String leaf;
    private final Set<String> allowedContentTypes;
    private final long maxSizeBytes;

    UploadCategory(String ownerRoot, String leaf, Set<String> allowedContentTypes, long maxSizeBytes) {
        this.ownerRoot = ownerRoot;
        this.leaf = leaf;
        this.allowedContentTypes = allowedContentTypes;
        this.maxSizeBytes = maxSizeBytes;
    }

    /** Key prefix (without the filename) for a given owner entity id. */
    public String keyPrefix(long ownerId) {
        return ownerRoot + "/" + ownerId + "/" + leaf;
    }

    /** Whether an object key does NOT belong under this category for the given owner. */
    public boolean isForeignKeyFor(long ownerId, String objectKey) {
        return objectKey == null || !objectKey.startsWith(keyPrefix(ownerId) + "/");
    }

    private static final class Rules {
        private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
        private static final long IMAGE_MAX_BYTES = 5L * 1024 * 1024;
    }
}
