package com.toppaload.project.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tasks")
class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "TODO";

    @Column(name = "priority", nullable = false, length = 16)
    private String priority = "MEDIUM";

    @Column(name = "estimated_hours", precision = 6, scale = 2)
    private BigDecimal estimatedHours;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent = 0;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "labels", columnDefinition = "text[]")
    private List<String> labels = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected TaskEntity() {}

    TaskEntity(
            ProjectEntity project,
            String title,
            String description,
            String status,
            String priority,
            BigDecimal estimatedHours,
            LocalDate startDate,
            LocalDate dueDate,
            int progressPercent,
            UUID assigneeId,
            List<String> labels
    ) {
        this.project = project;
        this.title = title;
        this.description = description;
        this.status = status != null ? status : "TODO";
        this.priority = priority != null ? priority : "MEDIUM";
        this.estimatedHours = estimatedHours;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.progressPercent = progressPercent;
        this.assigneeId = assigneeId;
        this.labels = labels != null ? new ArrayList<>(labels) : new ArrayList<>();
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

    ProjectEntity getProject() {
        return project;
    }

    void setProject(ProjectEntity project) {
        this.project = project;
    }

    String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    String getPriority() {
        return priority;
    }

    void setPriority(String priority) {
        this.priority = priority;
    }

    BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    void setEstimatedHours(BigDecimal estimatedHours) {
        this.estimatedHours = estimatedHours;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    LocalDate getDueDate() {
        return dueDate;
    }

    void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    int getProgressPercent() {
        return progressPercent;
    }

    void setProgressPercent(int progressPercent) {
        this.progressPercent = progressPercent;
    }

    UUID getAssigneeId() {
        return assigneeId;
    }

    void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
    }

    List<String> getLabels() {
        return labels;
    }

    void setLabels(List<String> labels) {
        this.labels = labels != null ? new ArrayList<>(labels) : new ArrayList<>();
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Instant getDeletedAt() {
        return deletedAt;
    }

    void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
