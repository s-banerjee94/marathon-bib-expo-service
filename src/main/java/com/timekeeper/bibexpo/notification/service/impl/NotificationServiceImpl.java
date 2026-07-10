package com.timekeeper.bibexpo.notification.service.impl;

import com.timekeeper.bibexpo.config.CacheConfig;
import com.timekeeper.bibexpo.exception.InvalidUserDataException;
import com.timekeeper.bibexpo.notification.model.dto.NotifyRequest;
import com.timekeeper.bibexpo.notification.model.dto.response.NotificationListResponse;
import com.timekeeper.bibexpo.notification.model.dto.response.NotificationResponse;
import com.timekeeper.bibexpo.notification.model.dynamodb.NotificationDDB;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.notification.repository.NotificationDDBRepository;
import com.timekeeper.bibexpo.notification.service.NotificationService;
import com.timekeeper.bibexpo.service.util.DynamoDBPaginationCodec;
import com.timekeeper.bibexpo.notification.service.util.NotificationRecipientResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private static final int NOTIFICATION_TTL_DAYS = 30;
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationDDBRepository notificationRepository;
    private final NotificationRecipientResolver recipientResolver;
    private final DynamoDBPaginationCodec paginationCodec;
    private final CacheManager cacheManager;
    private final Executor notificationTaskExecutor;

    @Override
    public void notify(NotifyRequest request) {
        // Best-effort and off the caller's thread: sending a notification must never add latency to,
        // or roll back, the business operation that triggered it.
        notificationTaskExecutor.execute(() -> doNotify(request));
    }

    private void doNotify(NotifyRequest request) {
        try {
            List<User> recipients = recipientResolver.resolve(request);
            Long actorId = request.getActor() != null ? request.getActor().getId() : null;
            String actorName = request.getActor() != null ? request.getActor().getUsername() : null;

            String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
            long expirationTime = Instant.now().plus(NOTIFICATION_TTL_DAYS, ChronoUnit.DAYS).getEpochSecond();

            List<NotificationDDB> rows = new ArrayList<>();
            for (User recipient : recipients) {
                if (actorId != null && actorId.equals(recipient.getId())) {
                    continue;
                }
                String key = now + "#" + UUID.randomUUID().toString().substring(0, 8);
                rows.add(NotificationDDB.builder()
                        .userId(recipient.getId())
                        .notificationKey(key)
                        .unreadKey(key)
                        .type(request.getType() != null ? request.getType().name() : null)
                        .title(request.getTitle())
                        .message(request.getMessage())
                        .entityType(request.getEntityType())
                        .entityId(request.getEntityId())
                        .audience(request.getAudience().name())
                        .actorName(actorName)
                        .read(false)
                        .createdAt(now)
                        .expirationTime(expirationTime)
                        .build());
            }

            if (rows.isEmpty()) {
                log.debug("No recipients for {} notification to audience {}", request.getType(), request.getAudience());
                return;
            }
            notificationRepository.saveAll(rows);
            evictUnreadCounts(rows);
            log.debug("Wrote {} '{}' notification(s) to audience {}", rows.size(), request.getType(), request.getAudience());
        } catch (Exception e) {
            log.error("Failed to send {} notification to audience {}", request.getType(), request.getAudience(), e);
        }
    }

    /** Drops the cached badge count for every recipient that just received a new notification. */
    private void evictUnreadCounts(List<NotificationDDB> rows) {
        Cache cache = cacheManager.getCache(CacheConfig.UNREAD_COUNTS_CACHE);
        if (cache == null) {
            return;
        }
        for (NotificationDDB row : rows) {
            cache.evict(row.getUserId());
        }
    }

    @Override
    public NotificationListResponse getNotifications(User user, int limit, String cursor) {
        int capped = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        Map<String, AttributeValue> startKey = unwrapCursor(cursor, user.getId());
        Page<NotificationDDB> page = notificationRepository.queryByUser(user.getId(), capped, startKey);
        return toListResponse(page, user.getId());
    }

    @Override
    @Cacheable(value = CacheConfig.UNREAD_COUNTS_CACHE, key = "#user.id")
    public long getUnreadCount(User user) {
        return notificationRepository.countUnread(user.getId());
    }

    @Override
    @CacheEvict(value = CacheConfig.UNREAD_COUNTS_CACHE, key = "#user.id")
    public void markAsRead(User user, String id) {
        notificationRepository.markRead(user.getId(), decodeId(id));
    }

    @Override
    @CacheEvict(value = CacheConfig.UNREAD_COUNTS_CACHE, key = "#user.id")
    public int markAllAsRead(User user) {
        return notificationRepository.markAllRead(user.getId());
    }

    @Override
    @CacheEvict(value = CacheConfig.UNREAD_COUNTS_CACHE, key = "#userId")
    public int deleteAllForUser(Long userId) {
        return notificationRepository.deleteAllByUser(userId);
    }

    private NotificationListResponse toListResponse(Page<NotificationDDB> page, Long userId) {
        if (page == null) {
            return NotificationListResponse.builder().items(List.of()).count(0).hasMore(false).build();
        }
        List<NotificationResponse> items = page.items().stream().map(this::toResponse).toList();
        String nextCursor = wrapCursor(page.lastEvaluatedKey(), userId);
        return NotificationListResponse.builder()
                .items(items)
                .count(items.size())
                .lastEvaluatedKey(nextCursor)
                .hasMore(nextCursor != null)
                .build();
    }

    private NotificationResponse toResponse(NotificationDDB n) {
        return NotificationResponse.builder()
                .id(encodeId(n.getNotificationKey()))
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .read(n.getRead())
                .entityType(n.getEntityType())
                .entityId(n.getEntityId())
                .actorName(n.getActorName())
                .createdAt(n.getCreatedAt())
                .build();
    }

    // The sort key contains ':' and '#', so expose it to the API as a URL-safe base64 token.
    private String encodeId(String notificationKey) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(notificationKey.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeId(String id) {
        try {
            return new String(Base64.getUrlDecoder().decode(id), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidUserDataException("Invalid notification id.");
        }
    }

    // Cursors are tagged with the owning user id so one user's cursor can't be replayed against another's feed.
    private String wrapCursor(Map<String, AttributeValue> raw, Long userId) {
        String encoded = paginationCodec.encode(raw);
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((userId + "|" + encoded).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, AttributeValue> unwrapCursor(String cursor, Long userId) {
        if (cursor == null || cursor.isBlank()) {
            return Map.of();
        }
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidUserDataException("Your pagination cursor is invalid. Start a new request.");
        }
        int sep = decoded.indexOf('|');
        if (sep < 0 || !String.valueOf(userId).equals(decoded.substring(0, sep))) {
            throw new InvalidUserDataException("Your pagination cursor is invalid. Start a new request.");
        }
        return paginationCodec.decode(decoded.substring(sep + 1));
    }
}
