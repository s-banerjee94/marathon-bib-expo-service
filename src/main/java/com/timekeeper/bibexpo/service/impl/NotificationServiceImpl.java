package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.UserNotFoundException;
import com.timekeeper.bibexpo.model.dto.response.NotificationResponse;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.model.entity.Notification;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.repository.NotificationRepository;
import com.timekeeper.bibexpo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements com.timekeeper.bibexpo.service.NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Notification createJobNotification(Long userId, Long eventId, Long jobExecutionId,
                                              int writeCount, int skipCount, String jobStatus) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        String title;
        String message;
        if ("COMPLETED".equals(jobStatus)) {
            title = "CSV Import Completed";
            message = String.format("Successfully imported %d participant(s) for event #%d. %d row(s) skipped.",
                    writeCount, eventId, skipCount);
        } else {
            title = "CSV Import Failed";
            message = String.format("Import job for event #%d ended with status %s. %d row(s) written, %d skipped.",
                    eventId, jobStatus, writeCount, skipCount);
        }

        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .eventId(eventId)
                .jobExecutionId(jobExecutionId)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.debug("Created notification {} for user {}", saved.getId(), userId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public PageableResponse<NotificationResponse> getNotifications(User user, int page, int size) {
        Page<Notification> resultPage = notificationRepository
                .findByUserOrderByCreatedAtDesc(user, PageRequest.of(page, size));
        Page<NotificationResponse> mapped = resultPage.map(this::toResponse);
        return PageableResponse.of(mapped);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(User user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }

    @Override
    @Transactional
    public void markAsRead(Long id, User user) {
        Notification notification = notificationRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .read(n.getRead())
                .eventId(n.getEventId())
                .jobExecutionId(n.getJobExecutionId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
