package com.toppaload.identity.internal;

import com.toppaload.audit.AuditRecorder;
import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.Role;
import com.toppaload.identity.UserDirectory;
import com.toppaload.identity.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
class UserAdminController {

    private final UserRepository userRepository;
    private final UserDirectory userDirectory;
    private final AuditRecorder auditRecorder;

    UserAdminController(UserRepository userRepository, UserDirectory userDirectory, AuditRecorder auditRecorder) {
        this.userRepository = userRepository;
        this.userDirectory = userDirectory;
        this.auditRecorder = auditRecorder;
    }

    record CreateUserRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be a valid email address")
            String email,

            @NotBlank(message = "Full name is required")
            @Size(max = 150, message = "Full name must not exceed 150 characters")
            String fullName,

            @NotNull(message = "Role is required")
            Role role,

            BigDecimal workHoursPerDay,

            List<Integer> workDays
    ) {}

    record PatchUserRequest(
            String fullName,
            Role role,
            Boolean active,
            BigDecimal workHoursPerDay,
            List<Integer> workDays
    ) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    List<UserSummary> listActiveUsers() {
        return userDirectory.listActiveUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    UserSummary createUser(@Valid @RequestBody CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use: " + email);
        }

        BigDecimal hours = request.workHoursPerDay() != null ? request.workHoursPerDay() : new BigDecimal("8.00");
        if (hours.compareTo(BigDecimal.ZERO) <= 0 || hours.compareTo(new BigDecimal("24.00")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workHoursPerDay must be > 0 and <= 24");
        }

        List<Integer> days = request.workDays() != null ? request.workDays() : List.of(1, 2, 3, 4, 5);
        validateWorkDays(days);

        UserEntity user = new UserEntity(
                email,
                request.fullName().trim(),
                request.role(),
                hours,
                days
        );
        user.setActive(true);
        user.setEmailNotifications(true);

        UserEntity saved = userRepository.save(user);

        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "CREATE",
                "USER",
                saved.getId().toString(),
                null,
                toSnapshot(saved)
        );

        return toSummary(saved);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    UserSummary patchUser(@PathVariable UUID id, @RequestBody PatchUserRequest request) {
        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.workDays() != null) {
            validateWorkDays(request.workDays());
        }

        if (request.workHoursPerDay() != null) {
            if (request.workHoursPerDay().compareTo(BigDecimal.ZERO) <= 0
                    || request.workHoursPerDay().compareTo(new BigDecimal("24.00")) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workHoursPerDay must be > 0 and <= 24");
            }
        }

        Map<String, Object> beforeData = toSnapshot(user);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        if (request.workHoursPerDay() != null) {
            user.setWorkHoursPerDay(request.workHoursPerDay());
        }
        if (request.workDays() != null) {
            user.setWorkDays(request.workDays());
        }

        UserEntity saved = userRepository.save(user);

        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "UPDATE",
                "USER",
                saved.getId().toString(),
                beforeData,
                toSnapshot(saved)
        );

        return toSummary(saved);
    }

    private void validateWorkDays(List<Integer> workDays) {
        if (workDays == null || workDays.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workDays must not be empty");
        }
        for (Integer d : workDays) {
            if (d == null || d < 1 || d > 7) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workDays values must be between 1 and 7");
            }
        }
    }

    private UserSummary toSummary(UserEntity user) {
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isActive(),
                user.getWorkHoursPerDay(),
                user.getWorkDays()
        );
    }

    private Map<String, Object> toSnapshot(UserEntity user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId().toString());
        map.put("email", user.getEmail());
        map.put("fullName", user.getFullName());
        map.put("role", user.getRole().name());
        map.put("active", user.isActive());
        map.put("workHoursPerDay", user.getWorkHoursPerDay());
        map.put("workDays", user.getWorkDays());
        return map;
    }
}
