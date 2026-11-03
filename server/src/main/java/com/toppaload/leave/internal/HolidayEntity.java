package com.toppaload.leave.internal;

import com.toppaload.leave.HolidayScope;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "holidays")
class HolidayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private HolidayScope scope = HolidayScope.NATIONAL;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected HolidayEntity() {}

    HolidayEntity(LocalDate holidayDate, String name, HolidayScope scope) {
        this.holidayDate = holidayDate;
        this.name = name;
        this.scope = scope != null ? scope : HolidayScope.NATIONAL;
    }

    @PrePersist
    void onPrePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onPreUpdate() {
        updatedAt = Instant.now();
    }

    UUID getId() {
        return id;
    }

    LocalDate getHolidayDate() {
        return holidayDate;
    }

    void setHolidayDate(LocalDate holidayDate) {
        this.holidayDate = holidayDate;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    HolidayScope getScope() {
        return scope;
    }

    void setScope(HolidayScope scope) {
        this.scope = scope;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    Instant getDeletedAt() {
        return deletedAt;
    }

    void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
