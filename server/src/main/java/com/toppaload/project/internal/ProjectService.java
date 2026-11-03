package com.toppaload.project.internal;

import com.toppaload.audit.AuditRecorder;
import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.UserDirectory;
import com.toppaload.identity.UserSummary;
import com.toppaload.project.ProjectLookup;
import com.toppaload.project.ProjectSummary;
import com.toppaload.project.TaskSummary;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
class ProjectService implements ProjectLookup {

    record CreateProjectRequest(
            @NotBlank(message = "Kode proyek wajib diisi")
            @Size(max = 24, message = "Kode proyek maksimal 24 karakter")
            String code,

            @NotBlank(message = "Nama proyek wajib diisi")
            @Size(max = 160, message = "Nama proyek maksimal 160 karakter")
            String name,

            String description,
            String status,

            @NotNull(message = "Tanggal mulai wajib diisi")
            LocalDate startDate,

            @NotNull(message = "Tanggal selesai wajib diisi")
            LocalDate endDate,

            UUID ownerId
    ) {}

    record UpdateProjectRequest(
            @Size(max = 24, message = "Kode proyek maksimal 24 karakter")
            String code,

            @Size(max = 160, message = "Nama proyek maksimal 160 karakter")
            String name,

            String description,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            UUID ownerId
    ) {}

    record CreateTaskRequest(
            @NotBlank(message = "Judul task wajib diisi")
            @Size(max = 200, message = "Judul task maksimal 200 karakter")
            String title,

            String description,
            String status,
            String priority,

            @DecimalMin(value = "0.0", message = "estimatedHours must be >= 0")
            BigDecimal estimatedHours,

            LocalDate startDate,
            LocalDate dueDate,

            @Min(value = 0, message = "progressPercent must be between 0 and 100")
            @Max(value = 100, message = "progressPercent must be between 0 and 100")
            Integer progressPercent,

            UUID assigneeId,
            List<String> labels
    ) {}

    record UpdateTaskRequest(
            @Size(max = 200, message = "Judul task maksimal 200 karakter")
            String title,

            String description,
            String status,
            String priority,

            @DecimalMin(value = "0.0", message = "estimatedHours must be >= 0")
            BigDecimal estimatedHours,

            LocalDate startDate,
            LocalDate dueDate,

            @Min(value = 0, message = "progressPercent must be between 0 and 100")
            @Max(value = 100, message = "progressPercent must be between 0 and 100")
            Integer progressPercent,

            UUID assigneeId,
            List<String> labels
    ) {}

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final UserDirectory userDirectory;
    private final AuditRecorder auditRecorder;

    ProjectService(
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            UserDirectory userDirectory,
            AuditRecorder auditRecorder
    ) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.userDirectory = userDirectory;
        this.auditRecorder = auditRecorder;
    }

    // --- ProjectLookup Implementation ---

    @Override
    @Transactional(readOnly = true)
    public ProjectSummary requireActiveProject(UUID projectId) {
        ProjectEntity project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan: " + projectId));
        return toSummary(project);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskSummary> findActiveTask(UUID taskId) {
        return taskRepository.findActiveWithProject(taskId)
                .map(this::toTaskSummary);
    }

    @Override
    public ProjectSummary findOrCreateActiveByCode(String code, String name, LocalDate startDate, LocalDate endDate, UUID ownerId) {
        String trimmedCode = code.trim();
        Optional<ProjectEntity> existing = projectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(trimmedCode);
        if (existing.isPresent()) {
            return toSummary(existing.get());
        }

        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tanggal selesai tidak boleh sebelum tanggal mulai");
        }

        ProjectEntity project = new ProjectEntity(
                trimmedCode.toUpperCase(),
                name.trim(),
                null,
                "ACTIVE",
                startDate,
                endDate,
                ownerId
        );
        ProjectEntity saved = projectRepository.save(project);

        try {
            AuthenticatedActor actor = userDirectory.requireCurrentActor();
            auditRecorder.record(actor.userId(), actor.email(), "CREATE", "PROJECT", saved.getId().toString(), null, toSnapshot(saved));
        } catch (Exception ignored) {
            // Background / cross-module invocation without actor context
        }

        return toSummary(saved);
    }

    // --- Project Operations ---

    @Transactional(readOnly = true)
    List<ProjectSummary> listProjects(String q, String status) {
        String normalizedQ = (q != null && !q.isBlank()) ? q.trim() : null;
        String normalizedStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        List<ProjectEntity> projects;
        if (normalizedQ != null && normalizedStatus != null) {
            projects = projectRepository.searchByQueryAndStatus(normalizedQ, normalizedStatus);
        } else if (normalizedQ != null) {
            projects = projectRepository.searchByQuery(normalizedQ);
        } else if (normalizedStatus != null) {
            projects = projectRepository.findByDeletedAtIsNullAndStatusOrderByCreatedAtDesc(normalizedStatus);
        } else {
            projects = projectRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }

        return projects.stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    ProjectSummary getProject(UUID id) {
        return requireActiveProject(id);
    }

    ProjectSummary createProject(CreateProjectRequest req) {
        String trimmedCode = req.code().trim();
        if (projectRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(trimmedCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kode proyek sudah digunakan");
        }

        if (req.endDate().isBefore(req.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tanggal selesai tidak boleh sebelum tanggal mulai");
        }

        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        UUID ownerId = req.ownerId() != null ? req.ownerId() : actor.userId();

        String status = (req.status() != null && !req.status().isBlank()) ? req.status().trim().toUpperCase() : "ACTIVE";

        ProjectEntity project = new ProjectEntity(
                trimmedCode.toUpperCase(),
                req.name().trim(),
                req.description(),
                status,
                req.startDate(),
                req.endDate(),
                ownerId
        );

        ProjectEntity saved = projectRepository.save(project);
        auditRecorder.record(actor.userId(), actor.email(), "CREATE", "PROJECT", saved.getId().toString(), null, toSnapshot(saved));

        return toSummary(saved);
    }

    ProjectSummary updateProject(UUID id, UpdateProjectRequest req) {
        ProjectEntity project = projectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan: " + id));

        LocalDate newStart = req.startDate() != null ? req.startDate() : project.getStartDate();
        LocalDate newEnd = req.endDate() != null ? req.endDate() : project.getEndDate();
        if (newEnd != null && newStart != null && newEnd.isBefore(newStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tanggal selesai tidak boleh sebelum tanggal mulai");
        }

        if (req.code() != null && !req.code().isBlank()) {
            String trimmedCode = req.code().trim();
            if (projectRepository.existsByCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(trimmedCode, id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Kode proyek sudah digunakan");
            }
            project.setCode(trimmedCode.toUpperCase());
        }

        Map<String, Object> beforeData = toSnapshot(project);

        if (req.name() != null && !req.name().isBlank()) {
            project.setName(req.name().trim());
        }
        if (req.description() != null) {
            project.setDescription(req.description());
        }
        if (req.status() != null && !req.status().isBlank()) {
            project.setStatus(req.status().trim().toUpperCase());
        }
        if (req.startDate() != null) {
            project.setStartDate(req.startDate());
        }
        if (req.endDate() != null) {
            project.setEndDate(req.endDate());
        }
        if (req.ownerId() != null) {
            project.setOwnerId(req.ownerId());
        }

        ProjectEntity saved = projectRepository.save(project);
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        auditRecorder.record(actor.userId(), actor.email(), "UPDATE", "PROJECT", saved.getId().toString(), beforeData, toSnapshot(saved));

        return toSummary(saved);
    }

    void deleteProject(UUID id) {
        ProjectEntity project = projectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan: " + id));

        Map<String, Object> beforeData = toSnapshot(project);
        Instant now = Instant.now();
        project.setDeletedAt(now);
        projectRepository.save(project);
        taskRepository.softDeleteAllByProjectId(id, now);

        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        auditRecorder.record(actor.userId(), actor.email(), "DELETE", "PROJECT", id.toString(), beforeData, null);
    }

    // --- Task Operations ---

    @Transactional(readOnly = true)
    List<TaskSummary> listTasks(UUID projectId, String q, String status) {
        requireActiveProject(projectId);
        String normalizedQ = (q != null && !q.isBlank()) ? q.trim() : null;
        String normalizedStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        List<TaskEntity> tasks;
        if (normalizedQ != null && normalizedStatus != null) {
            tasks = taskRepository.searchByQueryAndStatus(projectId, normalizedQ, normalizedStatus);
        } else if (normalizedQ != null) {
            tasks = taskRepository.searchByQuery(projectId, normalizedQ);
        } else if (normalizedStatus != null) {
            tasks = taskRepository.findByProjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(projectId, normalizedStatus);
        } else {
            tasks = taskRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId);
        }

        return tasks.stream()
                .map(this::toTaskSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    TaskSummary getTask(UUID id) {
        TaskEntity task = taskRepository.findActiveWithProject(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task tidak ditemukan: " + id));
        return toTaskSummary(task);
    }

    TaskSummary createTask(UUID projectId, CreateTaskRequest req) {
        ProjectEntity project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan: " + projectId));

        if (req.title() == null || req.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Judul task wajib diisi");
        }

        if (req.startDate() != null && req.dueDate() != null && req.dueDate().isBefore(req.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tanggal jatuh tempo tidak boleh sebelum tanggal mulai");
        }

        if (req.progressPercent() != null && (req.progressPercent() < 0 || req.progressPercent() > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "progressPercent must be between 0 and 100");
        }

        if (req.estimatedHours() != null && req.estimatedHours().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estimatedHours must be >= 0");
        }

        String status = (req.status() != null && !req.status().isBlank()) ? req.status().trim().toUpperCase() : "TODO";
        String priority = (req.priority() != null && !req.priority().isBlank()) ? req.priority().trim().toUpperCase() : "MEDIUM";
        int progress = req.progressPercent() != null ? req.progressPercent() : 0;

        TaskEntity task = new TaskEntity(
                project,
                req.title().trim(),
                req.description(),
                status,
                priority,
                req.estimatedHours(),
                req.startDate(),
                req.dueDate(),
                progress,
                req.assigneeId(),
                req.labels()
        );

        TaskEntity saved = taskRepository.save(task);
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        auditRecorder.record(actor.userId(), actor.email(), "CREATE", "TASK", saved.getId().toString(), null, toSnapshot(saved));

        return toTaskSummary(saved);
    }

    TaskSummary updateTask(UUID id, UpdateTaskRequest req) {
        TaskEntity task = taskRepository.findActiveWithProject(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task tidak ditemukan: " + id));

        if (req.title() != null && req.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Judul task tidak boleh kosong");
        }

        LocalDate start = req.startDate() != null ? req.startDate() : task.getStartDate();
        LocalDate due = req.dueDate() != null ? req.dueDate() : task.getDueDate();
        if (start != null && due != null && due.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tanggal jatuh tempo tidak boleh sebelum tanggal mulai");
        }

        if (req.progressPercent() != null && (req.progressPercent() < 0 || req.progressPercent() > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "progressPercent must be between 0 and 100");
        }

        if (req.estimatedHours() != null && req.estimatedHours().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estimatedHours must be >= 0");
        }

        Map<String, Object> beforeData = toSnapshot(task);

        if (req.title() != null && !req.title().isBlank()) {
            task.setTitle(req.title().trim());
        }
        if (req.description() != null) {
            task.setDescription(req.description());
        }
        if (req.status() != null && !req.status().isBlank()) {
            task.setStatus(req.status().trim().toUpperCase());
        }
        if (req.priority() != null && !req.priority().isBlank()) {
            task.setPriority(req.priority().trim().toUpperCase());
        }
        if (req.estimatedHours() != null) {
            task.setEstimatedHours(req.estimatedHours());
        }
        if (req.startDate() != null) {
            task.setStartDate(req.startDate());
        }
        if (req.dueDate() != null) {
            task.setDueDate(req.dueDate());
        }
        if (req.progressPercent() != null) {
            task.setProgressPercent(req.progressPercent());
        }
        if (req.assigneeId() != null) {
            task.setAssigneeId(req.assigneeId());
        }
        if (req.labels() != null) {
            task.setLabels(req.labels());
        }

        TaskEntity saved = taskRepository.save(task);
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        auditRecorder.record(actor.userId(), actor.email(), "UPDATE", "TASK", saved.getId().toString(), beforeData, toSnapshot(saved));

        return toTaskSummary(saved);
    }

    void deleteTask(UUID id) {
        TaskEntity task = taskRepository.findActiveWithProject(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task tidak ditemukan: " + id));

        Map<String, Object> beforeData = toSnapshot(task);
        task.setDeletedAt(Instant.now());
        taskRepository.save(task);

        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        auditRecorder.record(actor.userId(), actor.email(), "DELETE", "TASK", id.toString(), beforeData, null);
    }

    // --- Mappings & Snapshot Helpers ---

    private ProjectSummary toSummary(ProjectEntity project) {
        String ownerName = null;
        if (project.getOwnerId() != null) {
            ownerName = userDirectory.findActiveById(project.getOwnerId())
                    .map(UserSummary::fullName)
                    .orElse(null);
        }
        int taskCount = taskRepository.countByProjectIdAndDeletedAtIsNull(project.getId());

        return new ProjectSummary(
                project.getId(),
                project.getCode(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getStartDate(),
                project.getEndDate(),
                project.getOwnerId(),
                ownerName,
                taskCount,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private TaskSummary toTaskSummary(TaskEntity task) {
        String assigneeName = null;
        if (task.getAssigneeId() != null) {
            assigneeName = userDirectory.findActiveById(task.getAssigneeId())
                    .map(UserSummary::fullName)
                    .orElse(null);
        }

        return new TaskSummary(
                task.getId(),
                task.getProject().getId(),
                task.getProject().getCode(),
                task.getProject().getName(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getEstimatedHours(),
                task.getStartDate(),
                task.getDueDate(),
                task.getProgressPercent(),
                task.getAssigneeId(),
                assigneeName,
                task.getLabels() != null ? List.copyOf(task.getLabels()) : List.of(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private Map<String, Object> toSnapshot(ProjectEntity project) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", project.getId().toString());
        map.put("code", project.getCode());
        map.put("name", project.getName());
        map.put("description", project.getDescription());
        map.put("status", project.getStatus());
        map.put("startDate", project.getStartDate() != null ? project.getStartDate().toString() : null);
        map.put("endDate", project.getEndDate() != null ? project.getEndDate().toString() : null);
        map.put("ownerId", project.getOwnerId() != null ? project.getOwnerId().toString() : null);
        map.put("deletedAt", project.getDeletedAt() != null ? project.getDeletedAt().toString() : null);
        return map;
    }

    private Map<String, Object> toSnapshot(TaskEntity task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", task.getId().toString());
        map.put("projectId", task.getProject().getId().toString());
        map.put("title", task.getTitle());
        map.put("description", task.getDescription());
        map.put("status", task.getStatus());
        map.put("priority", task.getPriority());
        map.put("estimatedHours", task.getEstimatedHours());
        map.put("startDate", task.getStartDate() != null ? task.getStartDate().toString() : null);
        map.put("dueDate", task.getDueDate() != null ? task.getDueDate().toString() : null);
        map.put("progressPercent", task.getProgressPercent());
        map.put("assigneeId", task.getAssigneeId() != null ? task.getAssigneeId().toString() : null);
        map.put("labels", task.getLabels());
        map.put("deletedAt", task.getDeletedAt() != null ? task.getDeletedAt().toString() : null);
        return map;
    }
}
