package com.toppaload.identity.internal;

import com.toppaload.audit.AuditRecorder;
import com.toppaload.identity.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;
    private final String bootstrapAdminEmail;

    BootstrapAdminInitializer(
            UserRepository userRepository,
            AuditRecorder auditRecorder,
            @Value("${app.security.bootstrap-admin-email:admin@toppaload.local}") String bootstrapAdminEmail
    ) {
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
        this.bootstrapAdminEmail = bootstrapAdminEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = bootstrapAdminEmail != null && !bootstrapAdminEmail.isBlank()
                ? bootstrapAdminEmail.trim().toLowerCase()
                : "admin@toppaload.local";

        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).isEmpty()) {
            log.info("Creating bootstrap admin account with email: {}", email);
            UserEntity admin = new UserEntity(
                    email,
                    "Bootstrap Admin",
                    Role.ADMIN,
                    new BigDecimal("8.00"),
                    List.of(1, 2, 3, 4, 5)
            );
            admin.setActive(true);
            admin.setEmailNotifications(true);
            UserEntity saved = userRepository.save(admin);

            auditRecorder.record(
                    null,
                    email,
                    "CREATE",
                    "USER",
                    saved.getId().toString(),
                    null,
                    Map.of(
                            "email", email,
                            "role", Role.ADMIN.name(),
                            "source", "BOOTSTRAP"
                    )
            );
        }
    }
}
