package com.timekeeper.bibexpo.notification.controller;

import com.timekeeper.bibexpo.notification.model.dto.response.NotificationListResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController implements NotificationControllerApi {

    private final NotificationService notificationService;

    @Override
    public ResponseEntity<NotificationListResponse> getNotifications(int limit, String cursor, User currentUser) {
        return ResponseEntity.ok(notificationService.getNotifications(currentUser, limit, cursor));
    }

    @Override
    public ResponseEntity<Map<String, Long>> getUnreadCount(User currentUser) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(currentUser)));
    }

    @Override
    public ResponseEntity<Void> markAsRead(String id, User currentUser) {
        notificationService.markAsRead(currentUser, id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Map<String, Integer>> markAllAsRead(User currentUser) {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllAsRead(currentUser)));
    }
}
