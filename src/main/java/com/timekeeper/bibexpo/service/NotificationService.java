package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.model.dto.response.NotificationResponse;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.model.entity.Notification;
import com.timekeeper.bibexpo.model.entity.User;

/**
 * Service for managing in-app notifications.
 * Notifications are persisted to DB for history and delivered in real-time via SSE.
 */
public interface NotificationService {

    /**
     * Creates and persists a notification for a completed batch import job.
     *
     * @param userId         the ID of the user who launched the import
     * @param eventId        the event the import was for
     * @param jobExecutionId the Spring Batch job execution ID
     * @param writeCount     number of participants successfully imported
     * @param skipCount      number of rows skipped due to validation errors
     * @param jobStatus      final job status (COMPLETED, FAILED, etc.)
     * @return the persisted notification
     */
    Notification createJobNotification(Long userId, Long eventId, Long jobExecutionId,
                                       int writeCount, int skipCount, String jobStatus);

    /**
     * Returns paginated notifications for the given user, newest first.
     */
    PageableResponse<NotificationResponse> getNotifications(User user, int page, int size);

    /**
     * Returns the count of unread notifications for the given user.
     */
    long getUnreadCount(User user);

    /**
     * Marks a notification as read. Throws if not found or owned by a different user.
     */
    void markAsRead(Long id, User user);
}
