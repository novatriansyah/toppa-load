package com.toppaload.allocation.internal;

import com.toppaload.allocation.AllocationSummary;
import com.toppaload.allocation.AllocationType;
import com.toppaload.audit.AuditRecorder;
import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.Role;
import com.toppaload.identity.UserDirectory;
import com.toppaload.identity.UserSummary;
import com.toppaload.allocation.AllocationCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.toppaload.project.ProjectLookup;
import com.toppaload.project.ProjectSummary;
import com.toppaload.project.TaskSummary;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
class AllocationService {

    record CreateAllocationRequest(
            @NotNull(message = "ID pengguna wajib diisi")
            UUID userId,

            @NotNull(message = "ID proyek wajib diisi")
            UUID projectId,

            UUID taskId,

            @NotNull(message = "Tanggal mulai wajib diisi")
            LocalDate startDate,

            @NotNull(message = "Tanggal selesai wajib diisi")
            LocalDate endDate,

            @NotNull(message = "Jam per hari wajib diisi")
            @DecimalMin(value = "0.01", message = "Jam per hari harus > 0")
            @DecimalMax(value = "24.00", message = "Jam per hari maksimal 24")
            BigDecimal hoursPerDay,

            AllocationType allocationType,

            String notes
    ) {}

    record UpdateAllocationRequest(
            @NotNull(message = "ID pengguna wajib diisi")
            UUID userId,

            @NotNull(message = "ID proyek wajib diisi")
            UUID projectId,

            UUID taskId,

            @NotNull(message = "Tanggal mulai wajib diisi")
            LocalDate startDate,

            @NotNull(message = "Tanggal selesai wajib diisi")
            LocalDate endDate,

            @NotNull(message = "Jam per hari wajib diisi")
            @DecimalMin(value = "0.01", message = "Jam per hari harus > 0")
            @DecimalMax(value = "24.00", message = "Jam per hari maksimal 24")
            BigDecimal hoursPerDay,

            AllocationType allocationType,

            String notes
    ) {}

    private final AllocationRepository allocationRepository;
    private final UserDirectory userDirectory;
    private final ProjectLookup projectLookup;
    private final AuditRecorder auditRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean memberTentativeEnabled;

    AllocationService(
            AllocationRepository allocationRepository,
            UserDirectory userDirectory,
            ProjectLookup projectLookup,
            AuditRecorder auditRecorder,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.allocation.member-tentative-enabled:true}") boolean memberTentativeEnabled
    ) {
        this.allocationRepository = allocationRepository;
        this.userDirectory = userDirectory;
        this.projectLookup = projectLookup;
        this.auditRecorder = auditRecorder;
        this.eventPublisher = eventPublisher;
        this.memberTentativeEnabled = memberTentativeEnabled;
    }

    @Transactional(readOnly = true)
    public List<AllocationSummary> listAllocations(UUID userId, UUID projectId, LocalDate from, LocalDate to) {
        Specification<AllocationEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), to));
            }

            if (query != null) {
                query.orderBy(cb.asc(root.get("startDate")), cb.asc(root.get("createdAt")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<AllocationEntity> entities = allocationRepository.findAll(spec);
        return entities.stream().map(this::toSummary).toList();
    }

    public AllocationSummary createAllocation(CreateAllocationRequest request) {
        validateDatesAndHours(request.startDate(), request.endDate(), request.hoursPerDay());

        UserSummary user = userDirectory.findActiveById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pengguna tidak ditemukan atau tidak aktif"));

        ProjectSummary project = projectLookup.requireActiveProject(request.projectId());

        TaskSummary task = null;
        if (request.taskId() != null) {
            task = projectLookup.findActiveTask(request.taskId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task tidak ditemukan atau tidak aktif"));
            if (!task.projectId().equals(request.projectId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task tidak berada pada proyek yang dipilih");
            }
        }

        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        AllocationType type = request.allocationType() != null ? request.allocationType() : AllocationType.CONFIRMED;

        if (actor.role() == Role.MEMBER) {
            if (!memberTentativeEnabled) {
                throw new AccessDeniedException("Alokasi tentatif oleh anggota dinonaktifkan");
            }
            if (!request.userId().equals(actor.userId())) {
                throw new AccessDeniedException("Anggota hanya dapat membuat alokasi untuk diri sendiri");
            }
            if (type != AllocationType.TENTATIVE) {
                throw new AccessDeniedException("Anggota hanya dapat membuat alokasi bertipe TENTATIVE");
            }
        }

        AllocationEntity entity = new AllocationEntity();
        entity.setUserId(request.userId());
        entity.setProjectId(request.projectId());
        entity.setTaskId(request.taskId());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setHoursPerDay(request.hoursPerDay());
        entity.setAllocationType(type);
        entity.setNotes(request.notes());
        entity.setCreatedBy(actor.userId());

        AllocationEntity saved = allocationRepository.save(entity);

        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "CREATE",
                "ALLOCATION",
                saved.getId().toString(),
                null,
                toSnapshot(saved, user, project, task)
        );

        eventPublisher.publishEvent(new AllocationCreatedEvent(
                saved.getId(),
                user.id(),
                project.id(),
                project.name(),
                saved.getStartDate(),
                saved.getEndDate(),
                saved.getHoursPerDay(),
                saved.getAllocationType()
        ));

        return toSummary(saved, user, project, task);
    }

    public AllocationSummary updateAllocation(UUID id, UpdateAllocationRequest request) {
        validateDatesAndHours(request.startDate(), request.endDate(), request.hoursPerDay());

        AllocationEntity existing = allocationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alokasi tidak ditemukan"));

        UserSummary user = userDirectory.findActiveById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pengguna tidak ditemukan atau tidak aktif"));

        ProjectSummary project = projectLookup.requireActiveProject(request.projectId());

        TaskSummary task = null;
        if (request.taskId() != null) {
            task = projectLookup.findActiveTask(request.taskId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task tidak ditemukan atau tidak aktif"));
            if (!task.projectId().equals(request.projectId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task tidak berada pada proyek yang dipilih");
            }
        }

        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        AllocationType newType = request.allocationType() != null ? request.allocationType() : existing.getAllocationType();

        if (actor.role() == Role.MEMBER) {
            if (!existing.getUserId().equals(actor.userId()) || !request.userId().equals(actor.userId())) {
                throw new AccessDeniedException("Anggota hanya dapat mengubah alokasi miliknya sendiri");
            }
            if (existing.getAllocationType() == AllocationType.CONFIRMED || newType != AllocationType.TENTATIVE) {
                throw new AccessDeniedException("Anggota tidak dapat mengubah alokasi menjadi CONFIRMED");
            }
        }

        Map<String, Object> beforeSnapshot = toSnapshot(existing);

        existing.setUserId(request.userId());
        existing.setProjectId(request.projectId());
        existing.setTaskId(request.taskId());
        existing.setStartDate(request.startDate());
        existing.setEndDate(request.endDate());
        existing.setHoursPerDay(request.hoursPerDay());
        existing.setAllocationType(newType);
        existing.setNotes(request.notes());

        AllocationEntity saved = allocationRepository.save(existing);
        Map<String, Object> afterSnapshot = toSnapshot(saved, user, project, task);

        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "UPDATE",
                "ALLOCATION",
                saved.getId().toString(),
                beforeSnapshot,
                afterSnapshot
        );

        return toSummary(saved, user, project, task);
    }

    public void deleteAllocation(UUID id) {
        AllocationEntity existing = allocationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alokasi tidak ditemukan"));

        AuthenticatedActor actor = userDirectory.requireCurrentActor();

        if (actor.role() == Role.MEMBER) {
            if (!existing.getUserId().equals(actor.userId())) {
                throw new AccessDeniedException("Anggota hanya dapat menghapus alokasi miliknya sendiri");
            }
            if (existing.getAllocationType() == AllocationType.CONFIRMED) {
                throw new AccessDeniedException("Anggota tidak dapat menghapus alokasi CONFIRMED");
            }
        }

        Map<String, Object> beforeSnapshot = toSnapshot(existing);
        existing.setDeletedAt(Instant.now());
        allocationRepository.save(existing);

        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "DELETE",
                "ALLOCATION",
                existing.getId().toString(),
                beforeSnapshot,
                null
        );
    }

    private void validateDatesAndHours(LocalDate startDate, LocalDate endDate, BigDecimal hoursPerDay) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tanggal mulai dan selesai wajib diisi");
        }
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tanggal selesai tidak boleh sebelum tanggal mulai");
        }
        if (hoursPerDay == null || hoursPerDay.compareTo(BigDecimal.ZERO) <= 0 || hoursPerDay.compareTo(BigDecimal.valueOf(24)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jam per hari harus di antara 0.01 dan 24.00");
        }
    }

    private AllocationSummary toSummary(AllocationEntity entity) {
        UserSummary user = userDirectory.findActiveById(entity.getUserId()).orElse(null);
        ProjectSummary project = null;
        try {
            project = projectLookup.requireActiveProject(entity.getProjectId());
        } catch (Exception ignored) {}
        TaskSummary task = null;
        if (entity.getTaskId() != null) {
            task = projectLookup.findActiveTask(entity.getTaskId()).orElse(null);
        }
        return toSummary(entity, user, project, task);
    }

    private AllocationSummary toSummary(AllocationEntity entity, UserSummary user, ProjectSummary project, TaskSummary task) {
        String userName = user != null ? user.fullName() : null;
        String projectCode = project != null ? project.code() : null;
        String projectName = project != null ? project.name() : null;
        String taskTitle = task != null ? task.title() : null;

        return new AllocationSummary(
                entity.getId(),
                entity.getUserId(),
                userName,
                entity.getProjectId(),
                projectCode,
                projectName,
                entity.getTaskId(),
                taskTitle,
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getHoursPerDay(),
                entity.getAllocationType(),
                entity.getNotes(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private Map<String, Object> toSnapshot(AllocationEntity entity) {
        UserSummary user = userDirectory.findActiveById(entity.getUserId()).orElse(null);
        ProjectSummary project = null;
        try {
            project = projectLookup.requireActiveProject(entity.getProjectId());
        } catch (Exception ignored) {}
        TaskSummary task = null;
        if (entity.getTaskId() != null) {
            task = projectLookup.findActiveTask(entity.getTaskId()).orElse(null);
        }
        return toSnapshot(entity, user, project, task);
    }

    private Map<String, Object> toSnapshot(AllocationEntity entity, UserSummary user, ProjectSummary project, TaskSummary task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId() != null ? entity.getId().toString() : null);
        map.put("userId", entity.getUserId() != null ? entity.getUserId().toString() : null);
        map.put("userName", user != null ? user.fullName() : null);
        map.put("projectId", entity.getProjectId() != null ? entity.getProjectId().toString() : null);
        map.put("projectCode", project != null ? project.code() : null);
        map.put("projectName", project != null ? project.name() : null);
        map.put("taskId", entity.getTaskId() != null ? entity.getTaskId().toString() : null);
        map.put("taskTitle", task != null ? task.title() : null);
        map.put("startDate", entity.getStartDate() != null ? entity.getStartDate().toString() : null);
        map.put("endDate", entity.getEndDate() != null ? entity.getEndDate().toString() : null);
        map.put("hoursPerDay", entity.getHoursPerDay() != null ? entity.getHoursPerDay().toString() : null);
        map.put("allocationType", entity.getAllocationType() != null ? entity.getAllocationType().name() : null);
        map.put("notes", entity.getNotes());
        map.put("createdBy", entity.getCreatedBy() != null ? entity.getCreatedBy().toString() : null);
        map.put("deletedAt", entity.getDeletedAt() != null ? entity.getDeletedAt().toString() : null);
        return map;
    }
}

