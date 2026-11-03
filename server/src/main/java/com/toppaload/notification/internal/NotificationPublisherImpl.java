package com.toppaload.notification.internal;

import com.toppaload.identity.UserDirectory;
import com.toppaload.identity.UserSummary;
import com.toppaload.notification.NotificationPublisher;
import com.toppaload.notification.NotificationType;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
class NotificationPublisherImpl implements NotificationPublisher {

    private final NotificationRepository notificationRepository;
    private final SmtpEmailDispatcher emailDispatcher;
    private final TeamsWebhookDispatcher teamsWebhookDispatcher;
    private final UserDirectory userDirectory;

    NotificationPublisherImpl(
            NotificationRepository notificationRepository,
            SmtpEmailDispatcher emailDispatcher,
            TeamsWebhookDispatcher teamsWebhookDispatcher,
            UserDirectory userDirectory
    ) {
        this.notificationRepository = notificationRepository;
        this.emailDispatcher = emailDispatcher;
        this.teamsWebhookDispatcher = teamsWebhookDispatcher;
        this.userDirectory = userDirectory;
    }

    @Override
    public boolean publishIfAbsent(
            UUID userId,
            NotificationType type,
            String title,
            Map<String, Object> payload,
            String dedupKey
    ) {
        Optional<UserSummary> userOpt = userDirectory.findActiveById(userId);
        String initialEmailStatus = "PENDING";
        if (userOpt.isPresent() && !userOpt.get().emailNotifications()) {
            initialEmailStatus = "SKIPPED";
        }

        boolean inserted = notificationRepository.insertIfAbsent(
                userId,
                type,
                title,
                payload,
                dedupKey,
                initialEmailStatus
        );

        if (inserted) {
            if ("PENDING".equals(initialEmailStatus)) {
                emailDispatcher.dispatchPendingEmails();
            }
            if (type == NotificationType.OVERLOAD_WARNING) {
                String userName = userOpt.map(UserSummary::fullName).orElse(String.valueOf(userId));
                teamsWebhookDispatcher.sendAdaptiveCard(
                        "Peringatan Beban Kerja: " + userName,
                        title,
                        payload
                );
            }
        }

        return inserted;
    }
}

