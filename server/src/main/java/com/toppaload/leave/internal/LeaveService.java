package com.toppaload.leave.internal;

import com.toppaload.audit.AuditRecorder;
import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.Role;
import com.toppaload.identity.UserDirectory;
import com.toppaload.identity.UserSummary;
import com.toppaload.leave.LeavePortion;
import com.toppaload.leave.LeaveSummary;
import com.toppaload.leave.LeaveType;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
class LeaveService {

    record CreateLeaveRequest(
            @NotNull(message = "ID pengguna wajib diisi")
            UUID userId,

            @NotNull(message = "Tipe cuti wajib diisi")
            LeaveType leaveType,

            LeavePortion portion,

            @NotNull(message = "Tanggal mulai cuti wajib diisi")
            LocalDate startDate,

            @NotNull(message = "Tanggal selesai cuti wajib diisi")
            LocalDate endDate,

            String notes
    ) {}

    private final LeaveRepository leaveRepository;
    private final UserDirectory userDirectory;
    private final AuditRecorder auditRecorder;
    private final LeaveBalanceService leaveBalanceService;

    LeaveService(
            LeaveRepository leaveRepository,
            UserDirectory userDirectory,
            AuditRecorder auditRecorder,
            LeaveBalanceService leaveBalanceService
    ) {
        this.leaveRepository = leaveRepository;
        this.userDirectory = userDirectory;
        this.auditRecorder = auditRecorder;
        this.leaveBalanceService = leaveBalanceService;
    }

    LeaveSummary createLeave(CreateLeaveRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tanggal selesai tidak boleh sebelum tanggal mulai");
        }

        LeavePortion portion = request.portion() != null ? request.portion() : LeavePortion.FULL;
        if ((portion == LeavePortion.HALF_AM || portion == LeavePortion.HALF_PM) && !request.startDate().equals(request.endDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuti setengah hari hanya boleh diajukan untuk satu tanggal");
        }

        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        if (actor.role() == Role.MEMBER && !actor.userId().equals(request.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anggota hanya dapat mengajukan cuti untuk diri sendiri");
        }

        UserSummary targetUser = userDirectory.findActiveById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pengguna tidak ditemukan: " + request.userId()));

        String initialStatus = (actor.role() == Role.MEMBER) ? "PENDING" : "APPROVED";

        LeaveEntity entity = new LeaveEntity(
                request.userId(),
                request.leaveType(),
                portion,
                request.startDate(),
                request.endDate(),
                initialStatus,
                request.notes(),
                "APPROVED".equals(initialStatus) ? actor.userId() : null
        );

        LeaveEntity saved = leaveRepository.saveAndFlush(entity);

        if ("APPROVED".equals(initialStatus)) {
            leaveBalanceService.recalculateBalance(saved.getUserId(), saved.getStartDate().getYear());
        }

        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "CREATE",
                "LEAVE",
                saved.getId().toString(),
                null,
                toSnapshot(saved, targetUser)
        );

        return toSummary(saved, targetUser);
    }

    @Transactional(readOnly = true)
    List<LeaveSummary> listLeaves(LocalDate from, LocalDate to, UUID userId) {
        Specification<LeaveEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), to));
            }

            if (query != null) {
                query.orderBy(cb.asc(root.get("startDate")), cb.asc(root.get("id")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<LeaveEntity> entities = leaveRepository.findAll(spec);
        return entities.stream().map(entity -> {
            UserSummary user = userDirectory.findActiveById(entity.getUserId()).orElse(null);
            return toSummary(entity, user);
        }).toList();
    }

    LeaveSummary approveLeave(UUID id, UUID supervisorId) {
        LeaveEntity leave = leaveRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuti tidak ditemukan: " + id));

        UserSummary targetUser = userDirectory.findActiveById(leave.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pengguna tidak ditemukan: " + leave.getUserId()));

        UserSummary supervisor = userDirectory.findActiveById(supervisorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supervisor tidak ditemukan: " + supervisorId));

        Map<String, Object> before = toSnapshot(leave, targetUser);

        leave.setStatus("APPROVED");
        leave.setApproverId(supervisorId);
        LeaveEntity saved = leaveRepository.saveAndFlush(leave);

        leaveBalanceService.recalculateBalance(saved.getUserId(), saved.getStartDate().getYear());

        auditRecorder.record(
                supervisorId,
                supervisor.email(),
                "APPROVE",
                "LEAVE",
                saved.getId().toString(),
                before,
                toSnapshot(saved, targetUser)
        );

        return toSummary(saved, targetUser);
    }

    LeaveSummary rejectLeave(UUID id, UUID supervisorId) {
        LeaveEntity leave = leaveRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuti tidak ditemukan: " + id));

        UserSummary targetUser = userDirectory.findActiveById(leave.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pengguna tidak ditemukan: " + leave.getUserId()));

        UserSummary supervisor = userDirectory.findActiveById(supervisorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supervisor tidak ditemukan: " + supervisorId));

        Map<String, Object> before = toSnapshot(leave, targetUser);

        leave.setStatus("REJECTED");
        leave.setApproverId(supervisorId);
        LeaveEntity saved = leaveRepository.saveAndFlush(leave);

        leaveBalanceService.recalculateBalance(saved.getUserId(), saved.getStartDate().getYear());

        auditRecorder.record(
                supervisorId,
                supervisor.email(),
                "REJECT",
                "LEAVE",
                saved.getId().toString(),
                before,
                toSnapshot(saved, targetUser)
        );

        return toSummary(saved, targetUser);
    }

    void deleteLeave(UUID id) {
        LeaveEntity entity = leaveRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuti tidak ditemukan: " + id));

        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        if (actor.role() == Role.MEMBER && !actor.userId().equals(entity.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anggota hanya dapat membatalkan cuti milik sendiri");
        }

        UserSummary user = userDirectory.findActiveById(entity.getUserId()).orElse(null);
        Map<String, Object> beforeData = toSnapshot(entity, user);

        entity.setDeletedAt(Instant.now());
        leaveRepository.saveAndFlush(entity);

        leaveBalanceService.recalculateBalance(entity.getUserId(), entity.getStartDate().getYear());

        leaveBalanceService.recalculateBalance(entity.getUserId(), entity.getStartDate().getYear());

        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "DELETE",
                "LEAVE",
                entity.getId().toString(),
                beforeData,
                null
        );
    }

    private LeaveSummary toSummary(LeaveEntity entity, UserSummary user) {
        String email = user != null ? user.email() : null;
        String fullName = user != null ? user.fullName() : null;

        return new LeaveSummary(
                entity.getId(),
                entity.getUserId(),
                email,
                fullName,
                entity.getLeaveType(),
                entity.getPortion(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getCreatedAt()
        );
    }

    private Map<String, Object> toSnapshot(LeaveEntity entity, UserSummary user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId() != null ? entity.getId().toString() : null);
        map.put("userId", entity.getUserId() != null ? entity.getUserId().toString() : null);
        map.put("userEmail", user != null ? user.email() : null);
        map.put("leaveType", entity.getLeaveType() != null ? entity.getLeaveType().name() : null);
        map.put("portion", entity.getPortion() != null ? entity.getPortion().name() : null);
        map.put("startDate", entity.getStartDate() != null ? entity.getStartDate().toString() : null);
        map.put("endDate", entity.getEndDate() != null ? entity.getEndDate().toString() : null);
        map.put("status", entity.getStatus());
        map.put("notes", entity.getNotes());
        map.put("deletedAt", entity.getDeletedAt() != null ? entity.getDeletedAt().toString() : null);
        return map;
    }
}

