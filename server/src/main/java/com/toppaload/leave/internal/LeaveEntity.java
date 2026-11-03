package com.toppaload.leave.internal;

import com.toppaload.leave.LeavePortion;
import com.toppaload.leave.LeaveType;
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
@Table(name = "leaves")
class LeaveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType leaveType;

    @Enumerated(EnumType.STRING)
    @Column(name = "portion", nullable = false, length = 16)
    private LeavePortion portion = LeavePortion.FULL;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "APPROVED";

    @Column(name = "reason", length = 500)
    private String notes;

    @Column(name = "approver_id")
    private UUID approverId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected LeaveEntity() {}

    LeaveEntity(
            UUID userId,
            LeaveType leaveType,
            LeavePortion portion,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String notes,
            UUID approverId
    ) {
        this.userId = userId;
        this.leaveType = leaveType;
        this.portion = portion != null ? portion : LeavePortion.FULL;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status != null ? status : "APPROVED";
        this.notes = notes;
        this.approverId = approverId;
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

    UUID getUserId() {
        return userId;
    }

    void setUserId(UUID userId) {
        this.userId = userId;
    }

    LeaveType getLeaveType() {
        return leaveType;
    }

    void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    LeavePortion getPortion() {
        return portion;
    }

    void setPortion(LeavePortion portion) {
        this.portion = portion;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    LocalDate getEndDate() {
        return endDate;
    }

    void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    String getNotes() {
        return notes;
    }

    void setNotes(String notes) {
        this.notes = notes;
    }

    UUID getApproverId() {
        return approverId;
    }

    void setApproverId(UUID approverId) {
        this.approverId = approverId;
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
