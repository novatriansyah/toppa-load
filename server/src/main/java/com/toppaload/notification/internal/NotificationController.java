package com.toppaload.notification.internal;

import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.UserDirectory;
import com.toppaload.notification.NotificationType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserDirectory userDirectory;

    NotificationController(NotificationRepository notificationRepository, UserDirectory userDirectory) {
        this.notificationRepository = notificationRepository;
        this.userDirectory = userDirectory;
    }

    public record NotificationItemDto(
            UUID id,
            NotificationType type,
            String title,
            Map<String, Object> payload,
            Instant readAt,
            String emailStatus,
            Instant createdAt
    ) {}

    public record NotificationListResponse(
            List<NotificationItemDto> items,
            int unreadCount
    ) {}

    @GetMapping
    public NotificationListResponse listMyNotifications(
            @RequestParam(name = "limit", defaultValue = "30") int limit
    ) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        List<NotificationRepository.NotificationRow> rows =
                notificationRepository.findRecentByUserId(actor.userId(), limit);
        int unreadCount = notificationRepository.countUnreadByUserId(actor.userId());

        List<NotificationItemDto> items = rows.stream()
                .map(r -> new NotificationItemDto(
                        r.id(),
                        r.type(),
                        r.title(),
                        r.payload(),
                        r.readAt(),
                        r.emailStatus(),
                        r.createdAt()
                ))
                .toList();

        return new NotificationListResponse(items, unreadCount);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable("id") UUID id) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        notificationRepository.markAsRead(id, actor.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead() {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        int updated = notificationRepository.markAllAsRead(actor.userId());
        return ResponseEntity.ok(Map.of("markedCount", updated));
    }
}

