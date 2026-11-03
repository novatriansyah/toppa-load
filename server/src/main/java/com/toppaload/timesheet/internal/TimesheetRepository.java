package com.toppaload.timesheet.internal;

import com.toppaload.timesheet.TaskWorkloadComparisonDto;
import com.toppaload.timesheet.TimeEntryDto;
import com.toppaload.timesheet.TimesheetSubmissionDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class TimesheetRepository {

    private final JdbcClient jdbc;

    TimesheetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static TimesheetSubmissionDto mapSubmission(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime subAt = rs.getObject("submitted_at", OffsetDateTime.class);
        OffsetDateTime appAt = rs.getObject("approved_at", OffsetDateTime.class);
        String appByStr = rs.getString("approved_by");

        return new TimesheetSubmissionDto(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("user_id")),
                rs.getString("user_full_name"),
                rs.getObject("week_start_date", LocalDate.class),
                rs.getString("status"),
                subAt != null ? subAt.toInstant() : null,
                appAt != null ? appAt.toInstant() : null,
                appByStr != null ? UUID.fromString(appByStr) : null,
                rs.getString("reviewer_note")
        );
    }

    private static TimeEntryDto mapEntry(ResultSet rs, int rowNum) throws SQLException {
        String taskIdStr = rs.getString("task_id");
        String subIdStr = rs.getString("submission_id");
        return new TimeEntryDto(
                UUID.fromString(rs.getString("id")),
                subIdStr != null ? UUID.fromString(subIdStr) : null,
                UUID.fromString(rs.getString("user_id")),
                UUID.fromString(rs.getString("project_id")),
                rs.getString("project_code"),
                rs.getString("project_name"),
                taskIdStr != null ? UUID.fromString(taskIdStr) : null,
                rs.getString("task_title"),
                rs.getObject("entry_date", LocalDate.class),
                rs.getBigDecimal("hours"),
                rs.getString("notes")
        );
    }

    Optional<TimesheetSubmissionDto> findSubmission(UUID userId, LocalDate weekStartDate) {
        return jdbc.sql("""
            SELECT s.id, s.user_id, u.full_name as user_full_name, s.week_start_date,
                   s.status, s.submitted_at, s.approved_at, s.approved_by, s.reviewer_note
            FROM timesheet_submissions s
            JOIN users u ON u.id = s.user_id
            WHERE s.user_id = :userId AND s.week_start_date = :weekStartDate
        """)
        .param("userId", userId)
        .param("weekStartDate", weekStartDate)
        .query(TimesheetRepository::mapSubmission)
        .optional();
    }

    Optional<TimesheetSubmissionDto> findSubmissionById(UUID id) {
        return jdbc.sql("""
            SELECT s.id, s.user_id, u.full_name as user_full_name, s.week_start_date,
                   s.status, s.submitted_at, s.approved_at, s.approved_by, s.reviewer_note
            FROM timesheet_submissions s
            JOIN users u ON u.id = s.user_id
            WHERE s.id = :id
        """)
        .param("id", id)
        .query(TimesheetRepository::mapSubmission)
        .optional();
    }

    TimesheetSubmissionDto getOrCreateDraftSubmission(UUID userId, LocalDate weekStartDate) {
        return findSubmission(userId, weekStartDate).orElseGet(() -> {
            UUID id = UUID.randomUUID();
            jdbc.sql("""
                INSERT INTO timesheet_submissions (id, user_id, week_start_date, status)
                VALUES (:id, :userId, :weekStartDate, 'DRAFT')
                ON CONFLICT (user_id, week_start_date) DO NOTHING
            """)
            .param("id", id)
            .param("userId", userId)
            .param("weekStartDate", weekStartDate)
            .update();

            return findSubmission(userId, weekStartDate).orElseThrow();
        });
    }

    void updateSubmissionStatus(UUID id, String status, Instant submittedAt, Instant approvedAt, UUID approvedBy, String reviewerNote) {
        jdbc.sql("""
            UPDATE timesheet_submissions
            SET status = :status,
                submitted_at = :submittedAt,
                approved_at = :approvedAt,
                approved_by = :approvedBy,
                reviewer_note = :reviewerNote,
                updated_at = NOW()
            WHERE id = :id
        """)
        .param("id", id)
        .param("status", status)
        .param("submittedAt", submittedAt != null ? OffsetDateTime.ofInstant(submittedAt, java.time.ZoneOffset.UTC) : null)
        .param("approvedAt", approvedAt != null ? OffsetDateTime.ofInstant(approvedAt, java.time.ZoneOffset.UTC) : null)
        .param("approvedBy", approvedBy)
        .param("reviewerNote", reviewerNote)
        .update();
    }

    List<TimeEntryDto> findEntriesBySubmissionId(UUID submissionId) {
        return jdbc.sql("""
            SELECT e.id, e.submission_id, e.user_id, e.project_id, p.code as project_code, p.name as project_name,
                   e.task_id, t.title as task_title, e.entry_date, e.hours, e.notes
            FROM time_entries e
            JOIN projects p ON p.id = e.project_id
            LEFT JOIN tasks t ON t.id = e.task_id
            WHERE e.submission_id = :submissionId AND e.deleted_at IS NULL
            ORDER BY e.entry_date ASC, p.code ASC
        """)
        .param("submissionId", submissionId)
        .query(TimesheetRepository::mapEntry)
        .list();
    }

    void replaceEntries(UUID submissionId, UUID userId, List<com.toppaload.timesheet.SaveWeeklyTimesheetRequest.TimeEntryInput> entries) {
        jdbc.sql("DELETE FROM time_entries WHERE submission_id = :submissionId")
                .param("submissionId", submissionId)
                .update();

        if (entries == null || entries.isEmpty()) {
            return;
        }

        for (var input : entries) {
            jdbc.sql("""
                INSERT INTO time_entries (id, submission_id, user_id, project_id, task_id, entry_date, hours, notes)
                VALUES (:id, :submissionId, :userId, :projectId, :taskId, :entryDate, :hours, :notes)
            """)
            .param("id", input.id() != null ? input.id() : UUID.randomUUID())
            .param("submissionId", submissionId)
            .param("userId", userId)
            .param("projectId", input.projectId())
            .param("taskId", input.taskId())
            .param("entryDate", input.entryDate())
            .param("hours", input.hours())
            .param("notes", input.notes())
            .update();
        }
    }

    List<TaskWorkloadComparisonDto> computeComparisons(UUID userId, LocalDate weekStartDate, LocalDate weekEndDate) {
        return jdbc.sql("""
            WITH relevant_tasks AS (
                SELECT DISTINCT task_id, project_id FROM time_entries
                WHERE user_id = :userId AND entry_date BETWEEN :startDate AND :endDate AND deleted_at IS NULL AND task_id IS NOT NULL
                UNION
                SELECT DISTINCT task_id, project_id FROM allocations
                WHERE user_id = :userId AND end_date >= :startDate AND start_date <= :endDate AND deleted_at IS NULL AND task_id IS NOT NULL
            ),
            allocated AS (
                SELECT a.task_id,
                       SUM(a.hours_per_day * (LEAST(a.end_date, :endDate) - GREATEST(a.start_date, :startDate) + 1)) AS alloc_hours
                FROM allocations a
                WHERE a.user_id = :userId
                  AND a.end_date >= :startDate AND a.start_date <= :endDate
                  AND a.deleted_at IS NULL AND a.task_id IS NOT NULL
                GROUP BY a.task_id
            ),
            actual AS (
                SELECT e.task_id, SUM(e.hours) AS actual_hours
                FROM time_entries e
                WHERE e.user_id = :userId
                  AND e.entry_date BETWEEN :startDate AND :endDate
                  AND e.deleted_at IS NULL AND e.task_id IS NOT NULL
                GROUP BY e.task_id
            )
            SELECT t.id AS task_id, t.title AS task_title, p.id AS project_id, p.code AS project_code,
                   COALESCE(t.estimated_hours, 0) AS estimated_hours,
                   COALESCE(al.alloc_hours, 0) AS allocated_hours,
                   COALESCE(ac.actual_hours, 0) AS actual_hours
            FROM relevant_tasks rt
            JOIN tasks t ON t.id = rt.task_id
            JOIN projects p ON p.id = rt.project_id
            LEFT JOIN allocated al ON al.task_id = rt.task_id
            LEFT JOIN actual ac ON ac.task_id = rt.task_id
            ORDER BY p.code ASC, t.title ASC
        """)
        .param("userId", userId)
        .param("startDate", weekStartDate)
        .param("endDate", weekEndDate)
        .query((rs, rowNum) -> new TaskWorkloadComparisonDto(
                UUID.fromString(rs.getString("task_id")),
                rs.getString("task_title"),
                UUID.fromString(rs.getString("project_id")),
                rs.getString("project_code"),
                rs.getBigDecimal("estimated_hours"),
                rs.getBigDecimal("allocated_hours"),
                rs.getBigDecimal("actual_hours")
        ))
        .list();
    }

    List<TimesheetSubmissionDto> findPendingApprovals() {
        return jdbc.sql("""
            SELECT s.id, s.user_id, u.full_name as user_full_name, s.week_start_date,
                   s.status, s.submitted_at, s.approved_at, s.approved_by, s.reviewer_note
            FROM timesheet_submissions s
            JOIN users u ON u.id = s.user_id
            WHERE s.status = 'SUBMITTED'
            ORDER BY s.submitted_at ASC
        """)
        .query(TimesheetRepository::mapSubmission)
        .list();
    }
}

