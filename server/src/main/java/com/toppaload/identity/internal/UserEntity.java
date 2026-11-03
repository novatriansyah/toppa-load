package com.toppaload.identity.internal;

import com.toppaload.identity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "work_hours_per_day", nullable = false, precision = 4, scale = 2)
    private BigDecimal workHoursPerDay = new BigDecimal("8.00");

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "work_days", columnDefinition = "smallint[]")
    private List<Integer> workDays = new ArrayList<>(List.of(1, 2, 3, 4, 5));

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    UserEntity() {
    }

    UserEntity(String email, String fullName, Role role, BigDecimal workHoursPerDay, List<Integer> workDays) {
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        if (workHoursPerDay != null) {
            this.workHoursPerDay = workHoursPerDay;
        }
        if (workDays != null) {
            this.workDays = new ArrayList<>(workDays);
        }
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.workHoursPerDay == null) {
            this.workHoursPerDay = new BigDecimal("8.00");
        }
        if (this.workDays == null || this.workDays.isEmpty()) {
            this.workDays = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getEmail() {
        return email;
    }

    void setEmail(String email) {
        this.email = email;
    }

    String getFullName() {
        return fullName;
    }

    void setFullName(String fullName) {
        this.fullName = fullName;
    }

    Role getRole() {
        return role;
    }

    void setRole(Role role) {
        this.role = role;
    }

    BigDecimal getWorkHoursPerDay() {
        return workHoursPerDay;
    }

    void setWorkHoursPerDay(BigDecimal workHoursPerDay) {
        this.workHoursPerDay = workHoursPerDay;
    }

    List<Integer> getWorkDays() {
        return workDays;
    }

    void setWorkDays(List<Integer> workDays) {
        this.workDays = workDays != null ? new ArrayList<>(workDays) : new ArrayList<>();
    }

    boolean isActive() {
        return active;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    boolean isEmailNotifications() {
        return emailNotifications;
    }

    void setEmailNotifications(boolean emailNotifications) {
        this.emailNotifications = emailNotifications;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    Instant getDeletedAt() {
        return deletedAt;
    }

    void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
