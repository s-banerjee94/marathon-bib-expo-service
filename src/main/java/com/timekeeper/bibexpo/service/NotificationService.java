package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.notification.NotifyRequest;
import com.timekeeper.bibexpo.model.dto.response.NotificationListResponse;
import com.timekeeper.bibexpo.model.entity.User;

/**
 * In-app notifications: stored in DynamoDB (auto-expiring), targeted at an audience, and read by
 * short-polling. Sending is best-effort — a delivery failure is logged, never thrown, so it cannot
 * break the business operation that triggered it.
 */
public interface NotificationService {

    /**
     * Fans the request out to its audience and persists one notification row per recipient (the
     * triggering actor, if any, is excluded). Never throws.
     */
    void notify(NotifyRequest request);

    /**
     * Returns one page of the user's notifications, newest first.
     *
     * @param limit  max items per page (clamped to 1–50)
     * @param cursor opaque cursor from a previous page's {@code lastEvaluatedKey}; null for the first page
     */
    NotificationListResponse getNotifications(User user, int limit, String cursor);

    /** Count of the user's unread notifications (for the bell badge). */
    long getUnreadCount(User user);

    /** Marks one notification read. Idempotent — unknown or already-read ids are a no-op. */
    void markAsRead(User user, String id);

    /** Marks all of the user's notifications read; returns how many changed. */
    int markAllAsRead(User user);

    /** Deletes every notification for a user; returns how many were removed. Called on user deletion. */
    int deleteAllForUser(Long userId);
}
