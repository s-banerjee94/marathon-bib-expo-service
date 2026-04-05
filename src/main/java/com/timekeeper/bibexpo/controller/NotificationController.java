package com.timekeeper.bibexpo.controller;

import com.timekeeper.bibexpo.model.dto.response.NotificationResponse;
import com.timekeeper.bibexpo.model.dto.response.PageableResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.service.NotificationService;
import com.timekeeper.bibexpo.service.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController implements NotificationControllerApi {

    private final NotificationService notificationService;
    private final SseEmitterRegistry sseEmitterRegistry;

    @Override
    public SseEmitter subscribe(User currentUser) {
        log.info("SSE subscription - user: {}", currentUser.getUsername());
        return sseEmitterRegistry.subscribe(currentUser.getId());
    }

    @Override
    public ResponseEntity<PageableResponse<NotificationResponse>> getNotifications(
            int page, int size, User currentUser) {

        return ResponseEntity.ok(notificationService.getNotifications(currentUser, page, size));
    }

    @Override
    public ResponseEntity<Map<String, Long>> getUnreadCount(User currentUser) {
        long count = notificationService.getUnreadCount(currentUser);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Override
    public ResponseEntity<Void> markAsRead(Long id, User currentUser) {
        notificationService.markAsRead(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
