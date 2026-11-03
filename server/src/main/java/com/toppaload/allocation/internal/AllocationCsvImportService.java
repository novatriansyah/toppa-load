package com.toppaload.allocation.internal;

import com.toppaload.allocation.AllocationCreatedEvent;
import com.toppaload.allocation.AllocationType;
import com.toppaload.audit.AuditRecorder;
import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.UserDirectory;
import com.toppaload.identity.UserSummary;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class AllocationCsvImportService {

    private final AllocationRepository allocationRepository;
    private final UserDirectory userDirectory;
    private final AuditRecorder auditRecorder;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher eventPublisher;

    AllocationCsvImportService(
            AllocationRepository allocationRepository,
            UserDirectory userDirectory,
            AuditRecorder auditRecorder,
            JdbcClient jdbc,
            ApplicationEventPublisher eventPublisher
    ) {
        this.allocationRepository = allocationRepository;
        this.userDirectory = userDirectory;
        this.auditRecorder = auditRecorder;
        this.jdbc = jdbc;
        this.eventPublisher = eventPublisher;
    }

    public record CsvImportResult(
            int importedCount,
            int projectsCreated,
            int tasksCreated
    ) {}

    private record ParsedRow(
            int rowNum,
            String email,
            String projectCode,
            String projectName,
            String taskTitle,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal hoursPerDay,
            AllocationType allocationType,
            String notes
    ) {}

    @Transactional
    public CsvImportResult importAllocationsCsv(String csvContent) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();

        if (csvContent == null || csvContent.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Konten CSV kosong");
        }

        List<String> errors = new ArrayList<>();
        List<ParsedRow> parsedRows = new ArrayList<>();

        String[] lines = csvContent.split("\\r?\\n");
        int rowNumber = 0;

        for (String rawLine : lines) {
            rowNumber++;
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            // Skip header if line starts with email or "email"
            if (rowNumber == 1 && (line.toLowerCase().startsWith("email") || line.toLowerCase().startsWith("\"email\""))) {
                continue;
            }

            List<String> tokens = parseCsvLine(line);
            if (tokens.size() < 7) {
                errors.add("Baris " + rowNumber + ": Kolom tidak lengkap (minimal email, project_code, project_name, task_title, start_date, end_date, hours_per_day)");
                continue;
            }

            String email = cleanToken(tokens.get(0));
            String projectCode = cleanToken(tokens.get(1));
            String projectName = cleanToken(tokens.get(2));
            String taskTitle = cleanToken(tokens.get(3));
            String startDateStr = cleanToken(tokens.get(4));
            String endDateStr = cleanToken(tokens.get(5));
            String hoursStr = cleanToken(tokens.get(6));
            String allocTypeStr = tokens.size() > 7 ? cleanToken(tokens.get(7)) : "CONFIRMED";
            String notes = tokens.size() > 8 ? cleanToken(tokens.get(8)) : null;

            if (email.isBlank()) {
                errors.add("Baris " + rowNumber + ": Email wajib diisi");
            }
            if (projectCode.isBlank()) {
                errors.add("Baris " + rowNumber + ": Kode proyek wajib diisi");
            }
            if (projectName.isBlank()) {
                projectName = projectCode;
            }

            LocalDate startDate = null;
            try {
                startDate = LocalDate.parse(startDateStr);
            } catch (Exception e) {
                errors.add("Baris " + rowNumber + ": Format tanggal mulai tidak valid: '" + startDateStr + "'");
            }

            LocalDate endDate = null;
            try {
                endDate = LocalDate.parse(endDateStr);
            } catch (Exception e) {
                errors.add("Baris " + rowNumber + ": Format tanggal selesai tidak valid: '" + endDateStr + "'");
            }

            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                errors.add("Baris " + rowNumber + ": Tanggal selesai (" + endDate + ") tidak boleh sebelum tanggal mulai (" + startDate + ")");
            }

            BigDecimal hoursPerDay = null;
            try {
                hoursPerDay = new BigDecimal(hoursStr);
                if (hoursPerDay.compareTo(new BigDecimal("0.01")) < 0 || hoursPerDay.compareTo(new BigDecimal("24.00")) > 0) {
                    errors.add("Baris " + rowNumber + ": Jam per hari (" + hoursStr + ") harus antara 0.01 dan 24.00");
                }
            } catch (Exception e) {
                errors.add("Baris " + rowNumber + ": Format jam per hari tidak valid: '" + hoursStr + "'");
            }

            AllocationType type = AllocationType.CONFIRMED;
            if (!allocTypeStr.isBlank()) {
                try {
                    type = AllocationType.valueOf(allocTypeStr.toUpperCase());
                } catch (Exception e) {
                    type = AllocationType.CONFIRMED;
                }
            }

            if (startDate != null && endDate != null && hoursPerDay != null && !email.isBlank() && !projectCode.isBlank()) {
                parsedRows.add(new ParsedRow(
                        rowNumber,
                        email,
                        projectCode,
                        projectName,
                        taskTitle,
                        startDate,
                        endDate,
                        hoursPerDay,
                        type,
                        notes
                ));
            }
        }

        // Validate all emails against users allowlist
        Map<String, UUID> userMap = new HashMap<>();
        for (var row : parsedRows) {
            String lowerEmail = row.email().toLowerCase();
            if (!userMap.containsKey(lowerEmail)) {
                var userOpt = jdbc.sql("SELECT id FROM users WHERE LOWER(email) = :email AND active = true AND deleted_at IS NULL")
                        .param("email", lowerEmail)
                        .query(UUID.class)
                        .optional();
                if (userOpt.isEmpty()) {
                    errors.add("Baris " + row.rowNum() + ": Pengguna dengan email '" + row.email() + "' tidak terdaftar pada allowlist pengguna aktif");
                } else {
                    userMap.put(lowerEmail, userOpt.get());
                }
            }
        }

        // All-or-nothing: if any errors occurred, abort transaction and reject with 400!
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        }

        int projectsCreated = 0;
        int tasksCreated = 0;
        int importedAllocations = 0;

        Map<String, UUID> projectCache = new HashMap<>();
        Map<String, UUID> taskCache = new HashMap<>();

        for (var row : parsedRows) {
            UUID userId = userMap.get(row.email().toLowerCase());
            String upperCode = row.projectCode().toUpperCase();

            // Find or create project
            UUID projectId = projectCache.get(upperCode);
            if (projectId == null) {
                var existingProj = jdbc.sql("SELECT id FROM projects WHERE UPPER(code) = :code AND deleted_at IS NULL")
                        .param("code", upperCode)
                        .query(UUID.class)
                        .optional();
                if (existingProj.isPresent()) {
                    projectId = existingProj.get();
                } else {
                    projectId = UUID.randomUUID();
                    jdbc.sql("""
                        INSERT INTO projects (id, code, name, description, status, start_date, end_date, owner_id)
                        VALUES (:id, :code, :name, 'Diimpor via CSV onboarding', 'ACTIVE', :start, :end, :ownerId)
                    """)
                    .param("id", projectId)
                    .param("code", upperCode)
                    .param("name", row.projectName())
                    .param("start", row.startDate())
                    .param("end", row.endDate().isAfter(row.startDate().plusMonths(3)) ? row.endDate() : row.startDate().plusMonths(3))
                    .param("ownerId", actor.userId())
                    .update();
                    projectsCreated++;
                }
                projectCache.put(upperCode, projectId);
            }

            // Find or create task if taskTitle given
            UUID taskId = null;
            if (row.taskTitle() != null && !row.taskTitle().isBlank()) {
                String taskKey = projectId + ":" + row.taskTitle().toLowerCase();
                taskId = taskCache.get(taskKey);
                if (taskId == null) {
                    var existingTask = jdbc.sql("""
                        SELECT id FROM tasks
                        WHERE project_id = :projId AND LOWER(title) = :title AND deleted_at IS NULL
                    """)
                    .param("projId", projectId)
                    .param("title", row.taskTitle().toLowerCase())
                    .query(UUID.class)
                    .optional();

                    if (existingTask.isPresent()) {
                        taskId = existingTask.get();
                    } else {
                        taskId = UUID.randomUUID();
                        jdbc.sql("""
                            INSERT INTO tasks (id, project_id, title, status, priority, estimated_hours, due_date, assignee_id)
                            VALUES (:id, :projId, :title, 'TODO', 'MEDIUM', :est, :dueDate, :assigneeId)
                        """)
                        .param("id", taskId)
                        .param("projId", projectId)
                        .param("title", row.taskTitle())
                        .param("est", row.hoursPerDay().multiply(BigDecimal.valueOf(5)))
                        .param("dueDate", row.endDate())
                        .param("assigneeId", userId)
                        .update();
                        tasksCreated++;
                    }
                    taskCache.put(taskKey, taskId);
                }
            }

            // Insert allocation
            UUID allocId = UUID.randomUUID();
            jdbc.sql("""
                INSERT INTO allocations (id, user_id, project_id, task_id, start_date, end_date, hours_per_day, allocation_type, notes, created_by)
                VALUES (:id, :userId, :projId, :taskId, :start, :end, :hours, :type, :notes, :createdBy)
            """)
            .param("id", allocId)
            .param("userId", userId)
            .param("projId", projectId)
            .param("taskId", taskId)
            .param("start", row.startDate())
            .param("end", row.endDate())
            .param("hours", row.hoursPerDay())
            .param("type", row.allocationType().name())
            .param("notes", row.notes())
            .param("createdBy", actor.userId())
            .update();

            importedAllocations++;

            eventPublisher.publishEvent(new AllocationCreatedEvent(
                    allocId,
                    userId,
                    projectId,
                    row.projectName(),
                    row.startDate(),
                    row.endDate(),
                    row.hoursPerDay(),
                    row.allocationType()
            ));
        }

        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "IMPORT",
                "ALLOCATION",
                "BATCH",
                null,
                Map.of("importedCount", importedAllocations, "projectsCreated", projectsCreated, "tasksCreated", tasksCreated)
        );

        return new CsvImportResult(importedAllocations, projectsCreated, tasksCreated);
    }

    private List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens;
    }

    private String cleanToken(String token) {
        if (token == null) return "";
        String trimmed = token.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}

