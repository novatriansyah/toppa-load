package com.toppaload.notification;

import java.util.Map;
import java.util.UUID;

public interface NotificationPublisher {
    boolean publishIfAbsent(UUID userId, NotificationType type, String title, Map<String, Object> payload, String dedupKey);
}

