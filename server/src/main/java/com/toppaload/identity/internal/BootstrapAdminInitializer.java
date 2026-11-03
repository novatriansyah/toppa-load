package com.toppaload.identity.internal;

import com.toppaload.audit.AuditRecorder;
import com.toppaload.identity.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapAdminEmail;
    private final String bootstrapAdminPassword;

    BootstrapAdminInitializer(
            UserRepository userRepository,
            AuditRecorder auditRecorder,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.bootstrap-admin-email:admin@toppaload.local}") String bootstrapAdminEmail,
            @Value("${app.security.bootstrap-admin-password:admin123}") String bootstrapAdminPassword
    ) {
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapAdminEmail = bootstrapAdminEmail;
        this.bootstrapAdminPassword = bootstrapAdminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = bootstrapAdminEmail != null && !bootstrapAdminEmail.isBlank()
                ? bootstrapAdminEmail.trim().toLowerCase()
                : "admin@toppaload.local";
        String defaultPassword = bootstrapAdminPassword != null && !bootstrapAdminPassword.isBlank()
                ? bootstrapAdminPassword
                : "admin123";

        var existingOpt = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (existingOpt.isEmpty()) {
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
            admin.setPasswordHash(passwordEncoder.encode(defaultPassword));
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
        } else {
            UserEntity existing = existingOpt.get();
            if (existing.getPasswordHash() == null || existing.getPasswordHash().isBlank()) {
                log.info("Setting default password hash for existing bootstrap admin: {}", email);
                existing.setPasswordHash(passwordEncoder.encode(defaultPassword));
                userRepository.save(existing);
            }
        }
    }
}
