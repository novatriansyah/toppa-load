package com.toppaload.notification.internal;

import com.toppaload.identity.UserDirectory;
import com.toppaload.identity.UserSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
class SmtpEmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailDispatcher.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationRepository notificationRepository;
    private final UserDirectory userDirectory;
    private final String fromAddress;

    SmtpEmailDispatcher(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            NotificationRepository notificationRepository,
            UserDirectory userDirectory,
            @Value("${app.mail.from:noreply@toppaload.local}") String fromAddress
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.notificationRepository = notificationRepository;
        this.userDirectory = userDirectory;
        this.fromAddress = fromAddress;
    }

    public void dispatchPendingEmails() {
        List<NotificationRepository.NotificationRow> pending = notificationRepository.findPendingEmails(50);
        for (var row : pending) {
            Optional<UserSummary> userOpt = userDirectory.findActiveById(row.userId());
            if (userOpt.isEmpty()) {
                notificationRepository.updateEmailStatus(row.id(), "SKIPPED", null, "User not found or inactive");
                continue;
            }

            UserSummary user = userOpt.get();
            if (!user.emailNotifications()) {
                notificationRepository.updateEmailStatus(row.id(), "SKIPPED", null, "User disabled email notifications");
                continue;
            }

            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) {
                log.warn("JavaMailSender is not configured; skipping email dispatch for notification {}", row.id());
                notificationRepository.updateEmailStatus(row.id(), "SKIPPED", null, "JavaMailSender not configured");
                continue;
            }

            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(user.email());
                message.setSubject("[Toppa Load] " + row.title());
                message.setText("Halo " + user.fullName() + ",\n\n" + row.title() + "\n\nDetail:\n" + row.payload() + "\n\nSalam,\nTim Toppa Load");

                mailSender.send(message);
                notificationRepository.updateEmailStatus(row.id(), "SENT", Instant.now(), null);
            } catch (Exception e) {
                log.error("Failed to send email for notification {}: {}", row.id(), e.getMessage());
                notificationRepository.updateEmailStatus(row.id(), "FAILED", null, e.getMessage());
            }
        }
    }
}

