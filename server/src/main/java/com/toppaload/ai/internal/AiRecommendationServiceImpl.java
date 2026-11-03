package com.toppaload.ai.internal;

import com.toppaload.ai.AiRecommendationService;
import com.toppaload.ai.ApplyBalanceSuggestionRequest;
import com.toppaload.ai.ApplyBalanceSuggestionResponse;
import com.toppaload.ai.AssigneeRecommendationDto;
import com.toppaload.ai.TaskRecommendationResponse;
import com.toppaload.ai.WorkloadBalanceSuggestionDto;
import com.toppaload.audit.AuditRecorder;
import com.toppaload.capacity.CapacityCell;
import com.toppaload.capacity.CapacityQueryService;
import com.toppaload.capacity.Granularity;
import com.toppaload.capacity.HeatmapResponse;
import com.toppaload.capacity.UserHeatmapRow;
import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.Role;
import com.toppaload.identity.SystemSettingsDto;
import com.toppaload.identity.SystemSettingsReader;
import com.toppaload.identity.UserDirectory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class AiRecommendationServiceImpl implements AiRecommendationService {

    private final JdbcClient jdbc;
    private final CapacityQueryService capacityQueryService;
    private final SystemSettingsReader systemSettingsReader;
    private final UserDirectory userDirectory;
    private final AuditRecorder auditRecorder;

    AiRecommendationServiceImpl(
            JdbcClient jdbc,
            CapacityQueryService capacityQueryService,
            SystemSettingsReader systemSettingsReader,
            UserDirectory userDirectory,
            AuditRecorder auditRecorder
    ) {
        this.jdbc = jdbc;
        this.capacityQueryService = capacityQueryService;
        this.systemSettingsReader = systemSettingsReader;
        this.userDirectory = userDirectory;
        this.auditRecorder = auditRecorder;
    }

    @Override
    public TaskRecommendationResponse getRecommendationsForTask(UUID taskId) {
        SystemSettingsDto settings = systemSettingsReader.currentSettings();
        boolean enabled = settings.aiRecommendationsEnabled() == null || settings.aiRecommendationsEnabled();

        var taskOpt = jdbc.sql("""
            SELECT id, title, description, estimated_hours, start_date, due_date
            FROM tasks
            WHERE id = :id AND deleted_at IS NULL
        """)
        .param("id", taskId)
        .query((rs, rowNum) -> new TaskMeta(
                (UUID) rs.getObject("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getBigDecimal("estimated_hours"),
                rs.getDate("start_date") != null ? rs.getDate("start_date").toLocalDate() : null,
                rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null
        ))
        .optional();

        if (taskOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task tidak ditemukan: " + taskId);
        }

        TaskMeta task = taskOpt.get();
        if (!enabled) {
            return new TaskRecommendationResponse(task.id(), task.title(), false, List.of());
        }

        LocalDate start = (task.startDate() != null) ? task.startDate() : LocalDate.now();
        LocalDate due = (task.dueDate() != null && !task.dueDate().isBefore(start)) ? task.dueDate() : start.plusWeeks(2);
        BigDecimal taskHours = (task.estimatedHours() != null && task.estimatedHours().compareTo(BigDecimal.ZERO) > 0)
                ? task.estimatedHours()
                : BigDecimal.valueOf(8.0);

        BigDecimal overThreshold = settings.overloadedThresholdPct();

        // Query active users
        List<UserMeta> users = jdbc.sql("""
            SELECT id, email, full_name, role
            FROM users
            WHERE active = TRUE AND deleted_at IS NULL
        """)
        .query((rs, rowNum) -> new UserMeta(
                (UUID) rs.getObject("id"),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getString("role")
        ))
        .list();

        // Query user skills
        Map<UUID, List<SkillMeta>> userSkillsMap = new HashMap<>();
        jdbc.sql("""
            SELECT user_id, skill_name, proficiency_level
            FROM user_skills
        """)
        .query((rs, rowNum) -> new SkillMeta(
                (UUID) rs.getObject("user_id"),
                rs.getString("skill_name"),
                rs.getString("proficiency_level")
        ))
        .list()
        .forEach(s -> userSkillsMap.computeIfAbsent(s.userId(), k -> new ArrayList<>()).add(s));

        // Get capacity heatmap for the task time frame
        HeatmapResponse heatmap = capacityQueryService.getHeatmap(start, due, Granularity.WEEK, false);
        Map<UUID, UserCapacitySummary> capacityMap = new HashMap<>();

        for (UserHeatmapRow row : heatmap.rows()) {
            BigDecimal totalNetCap = BigDecimal.ZERO;
            BigDecimal totalConfirmed = BigDecimal.ZERO;

            for (CapacityCell cell : row.cells()) {
                totalNetCap = totalNetCap.add(cell.netCapacityHours() != null ? cell.netCapacityHours() : BigDecimal.ZERO);
                totalConfirmed = totalConfirmed.add(cell.confirmedHours() != null ? cell.confirmedHours() : BigDecimal.ZERO);
            }

            BigDecimal available = totalNetCap.subtract(totalConfirmed).max(BigDecimal.ZERO);
            BigDecimal currentUtilPct = totalNetCap.compareTo(BigDecimal.ZERO) > 0
                    ? totalConfirmed.multiply(BigDecimal.valueOf(100)).divide(totalNetCap, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            capacityMap.put(row.userId(), new UserCapacitySummary(totalNetCap, totalConfirmed, available, currentUtilPct));
        }

        String searchContext = (task.title() + " " + (task.description() != null ? task.description() : "")).toLowerCase(Locale.ROOT);

        List<AssigneeRecommendationDto> recs = new ArrayList<>();

        for (UserMeta u : users) {
            UserCapacitySummary cap = capacityMap.getOrDefault(u.id(),
                    new UserCapacitySummary(BigDecimal.valueOf(40.0), BigDecimal.ZERO, BigDecimal.valueOf(40.0), BigDecimal.ZERO));

            List<SkillMeta> skills = userSkillsMap.getOrDefault(u.id(), List.of());

            // Match skills against task context
            List<String> matchedSkillNames = new ArrayList<>();
            int skillScore = 15; // default base score

            for (SkillMeta s : skills) {
                if (searchContext.contains(s.skillName().toLowerCase(Locale.ROOT))) {
                    matchedSkillNames.add(s.skillName() + " (" + s.proficiencyLevel() + ")");
                    int pScore = switch (s.proficiencyLevel()) {
                        case "EXPERT" -> 40;
                        case "ADVANCED" -> 35;
                        case "INTERMEDIATE" -> 25;
                        default -> 15;
                    };
                    if (pScore > skillScore) skillScore = pScore;
                }
            }

            // Free capacity score (0..45)
            int capacityScore;
            if (cap.availableHours().compareTo(taskHours) >= 0) {
                capacityScore = 45;
            } else if (cap.availableHours().compareTo(BigDecimal.ZERO) > 0) {
                capacityScore = cap.availableHours().multiply(BigDecimal.valueOf(40))
                        .divide(taskHours, 0, RoundingMode.HALF_UP).intValue();
            } else {
                capacityScore = 5;
            }

            // Current workload score (0..15)
            int loadScore;
            if (cap.currentUtilPct().compareTo(BigDecimal.valueOf(60)) < 0) {
                loadScore = 15; // Underutilized, great candidate
            } else if (cap.currentUtilPct().compareTo(BigDecimal.valueOf(85)) <= 0) {
                loadScore = 10; // Optimal
            } else {
                loadScore = 0; // Already busy
            }

            int matchScore = Math.min(100, Math.max(10, capacityScore + skillScore + loadScore));

            BigDecimal projectedHours = cap.confirmedHours().add(taskHours);
            BigDecimal projectedUtilPct = cap.netCapacityHours().compareTo(BigDecimal.ZERO) > 0
                    ? projectedHours.multiply(BigDecimal.valueOf(100)).divide(cap.netCapacityHours(), 1, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100.0);

            boolean willOverload = projectedUtilPct.compareTo(overThreshold) >= 0;
            if (willOverload) {
                matchScore = Math.max(10, matchScore - 25);
            }

            // Build clear explanation
            StringBuilder reason = new StringBuilder();
            reason.append(u.fullName()).append(" memiliki ").append(cap.availableHours().setScale(1, RoundingMode.HALF_UP))
                  .append(" jam luang pada rentang waktu task.");

            if (!matchedSkillNames.isEmpty()) {
                reason.append(" Memiliki kecocokan keahlian: ").append(String.join(", ", matchedSkillNames)).append(".");
            }

            if (willOverload) {
                reason.append(" PERINGATAN: Penugasan ini berisiko menyebabkan beban kerja berlebih (proyeksi ").append(projectedUtilPct).append("%).");
            } else if (cap.currentUtilPct().compareTo(BigDecimal.valueOf(70)) < 0) {
                reason.append(" Sangat direkomendasikan untuk menyeimbangkan beban tim (saat ini ").append(cap.currentUtilPct()).append("%).");
            } else {
                reason.append(" Kapasitas kerja memadai dan stabil.");
            }

            recs.add(new AssigneeRecommendationDto(
                    u.id(),
                    u.fullName(),
                    u.email(),
                    u.role(),
                    matchScore,
                    cap.availableHours().setScale(1, RoundingMode.HALF_UP),
                    cap.currentUtilPct(),
                    projectedUtilPct,
                    willOverload,
                    matchedSkillNames,
                    reason.toString()
            ));
        }

        recs.sort(Comparator.comparingInt(AssigneeRecommendationDto::matchScore).reversed());

        return new TaskRecommendationResponse(task.id(), task.title(), true, recs);
    }

    @Override
    public List<WorkloadBalanceSuggestionDto> getTeamBalanceSuggestions() {
        SystemSettingsDto settings = systemSettingsReader.currentSettings();
        boolean enabled = settings.aiRecommendationsEnabled() == null || settings.aiRecommendationsEnabled();
        if (!enabled) return List.of();

        LocalDate today = LocalDate.now();
        LocalDate end = today.plusWeeks(2);

        HeatmapResponse heatmap = capacityQueryService.getHeatmap(today, end, Granularity.WEEK, false);
        BigDecimal overThreshold = settings.overloadedThresholdPct();
        BigDecimal underThreshold = settings.underutilizedThresholdPct();

        List<UserCapacitySummaryWithName> overloaded = new ArrayList<>();
        List<UserCapacitySummaryWithName> underutilized = new ArrayList<>();

        for (UserHeatmapRow row : heatmap.rows()) {
            BigDecimal totalNetCap = BigDecimal.ZERO;
            BigDecimal totalConfirmed = BigDecimal.ZERO;

            for (CapacityCell cell : row.cells()) {
                totalNetCap = totalNetCap.add(cell.netCapacityHours() != null ? cell.netCapacityHours() : BigDecimal.ZERO);
                totalConfirmed = totalConfirmed.add(cell.confirmedHours() != null ? cell.confirmedHours() : BigDecimal.ZERO);
            }

            BigDecimal utilPct = totalNetCap.compareTo(BigDecimal.ZERO) > 0
                    ? totalConfirmed.multiply(BigDecimal.valueOf(100)).divide(totalNetCap, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal availableHours = totalNetCap.subtract(totalConfirmed).max(BigDecimal.ZERO);

            // Filter out non-developer accounts (e.g. SUPERVISOR and Bootstrap Admin) from receiving developer workload
            boolean isEligibleCandidate = "MEMBER".equalsIgnoreCase(row.role())
                    && !"Bootstrap Admin".equalsIgnoreCase(row.fullName())
                    && !"Steelmaking AppDev".equalsIgnoreCase(row.fullName())
                    && totalNetCap.compareTo(BigDecimal.ZERO) > 0;

            if (utilPct.compareTo(overThreshold) >= 0) {
                overloaded.add(new UserCapacitySummaryWithName(row.userId(), row.fullName(), row.role(), utilPct, availableHours));
            } else if (isEligibleCandidate && utilPct.compareTo(underThreshold) <= 0) {
                underutilized.add(new UserCapacitySummaryWithName(row.userId(), row.fullName(), row.role(), utilPct, availableHours));
            }
        }

        List<WorkloadBalanceSuggestionDto> suggestions = new ArrayList<>();

        for (UserCapacitySummaryWithName over : overloaded) {
            // Find active allocations of the overloaded user in the current window
            List<AllocationMeta> activeAllocs = jdbc.sql("""
                SELECT a.id, a.project_id, p.code as project_code, p.name as project_name,
                       a.task_id, t.title as task_title, a.hours_per_day, a.start_date, a.end_date
                FROM allocations a
                JOIN projects p ON p.id = a.project_id
                LEFT JOIN tasks t ON t.id = a.task_id
                WHERE a.user_id = :userId
                  AND a.deleted_at IS NULL
                  AND a.end_date >= :windowStart
                  AND a.start_date <= :windowEnd
                ORDER BY a.hours_per_day ASC
            """)
            .param("userId", over.userId())
            .param("windowStart", today)
            .param("windowEnd", end)
            .query((rs, rowNum) -> new AllocationMeta(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("project_id"),
                    rs.getString("project_code"),
                    rs.getString("project_name"),
                    rs.getObject("task_id") != null ? (UUID) rs.getObject("task_id") : null,
                    rs.getString("task_title"),
                    rs.getBigDecimal("hours_per_day"),
                    rs.getDate("start_date").toLocalDate(),
                    rs.getDate("end_date").toLocalDate()
            ))
            .list();

            for (UserCapacitySummaryWithName under : underutilized) {
                if (over.userId().equals(under.userId())) continue;

                if (!activeAllocs.isEmpty()) {
                    for (AllocationMeta alloc : activeAllocs) {
                        String taskDesc = alloc.taskTitle() != null ? "Task '" + alloc.taskTitle() + "'" : "alokasi proyek";
                        String text = String.format(
                                "Beban kerja %s mencapai %s%% (Overload). Alihkan %s pada proyek [%s] (%s jam/hari) kepada %s yang saat ini berutilisasi %s%% (%s jam luang) untuk menyeimbangkan beban tim.",
                                over.fullName(), over.utilPct(), taskDesc, alloc.projectCode(), alloc.hoursPerDay(),
                                under.fullName(), under.utilPct(), under.availableHours()
                        );

                        suggestions.add(new WorkloadBalanceSuggestionDto(
                                over.userId(),
                                over.fullName(),
                                over.utilPct(),
                                under.userId(),
                                under.fullName(),
                                under.utilPct(),
                                under.availableHours(),
                                alloc.id(),
                                alloc.projectId(),
                                alloc.projectCode(),
                                alloc.projectName(),
                                alloc.taskId(),
                                alloc.taskTitle(),
                                alloc.hoursPerDay(),
                                alloc.startDate(),
                                alloc.endDate(),
                                text,
                                under.role()
                        ));
                    }
                } else {
                    String text = String.format(
                            "Beban kerja %s mencapai %s%% (Overload). Pertimbangkan untuk mengalihkan alokasi mendatang kepada %s yang saat ini berutilisasi %s%% (%s jam luang).",
                            over.fullName(), over.utilPct(), under.fullName(), under.utilPct(), under.availableHours()
                    );

                    suggestions.add(new WorkloadBalanceSuggestionDto(
                            over.userId(),
                            over.fullName(),
                            over.utilPct(),
                            under.userId(),
                            under.fullName(),
                            under.utilPct(),
                            under.availableHours(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            today,
                            end,
                            text,
                            under.role()
                    ));
                }
            }
        }

        return suggestions;
    }

    @Override
    @Transactional
    public ApplyBalanceSuggestionResponse applyBalanceSuggestion(ApplyBalanceSuggestionRequest request) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        if (actor.role() != Role.ADMIN && actor.role() != Role.SUPERVISOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya Admin atau Supervisor yang dapat menyetujui penyeimbangan alokasi.");
        }

        var candidateOpt = jdbc.sql("SELECT id, full_name FROM users WHERE id = :id AND deleted_at IS NULL AND active = TRUE")
                .param("id", request.candidateUserId())
                .query((rs, rowNum) -> Map.of(
                        "id", (UUID) rs.getObject("id"),
                        "name", rs.getString("full_name")
                ))
                .optional();
        if (candidateOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Kandidat pengguna tidak ditemukan atau tidak aktif.");
        }
        String candidateName = (String) candidateOpt.get().get("name");

        var allocOpt = jdbc.sql("SELECT id, user_id, project_id, task_id, hours_per_day, start_date, end_date FROM allocations WHERE id = :id AND deleted_at IS NULL")
                .param("id", request.allocationId())
                .query((rs, rowNum) -> Map.of(
                        "id", (UUID) rs.getObject("id"),
                        "userId", (UUID) rs.getObject("user_id"),
                        "taskId", rs.getObject("task_id") != null ? (UUID) rs.getObject("task_id") : null
                ))
                .optional();
        if (allocOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alokasi tidak ditemukan: " + request.allocationId());
        }

        UUID oldUserId = (UUID) allocOpt.get().get("userId");

        // 1. Update allocation target user
        jdbc.sql("UPDATE allocations SET user_id = :userId, updated_at = NOW() WHERE id = :id")
                .param("userId", request.candidateUserId())
                .param("id", request.allocationId())
                .update();

        // 2. If allocation is linked to a task, reassign task as well
        UUID taskIdToUpdate = request.taskId() != null ? request.taskId() : (UUID) allocOpt.get().get("taskId");
        if (taskIdToUpdate != null) {
            jdbc.sql("UPDATE tasks SET assignee_id = :assigneeId, updated_at = NOW() WHERE id = :taskId AND deleted_at IS NULL")
                    .param("assigneeId", request.candidateUserId())
                    .param("taskId", taskIdToUpdate)
                    .update();
        }

        // 3. Record audit log
        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "REBALANCE",
                "ALLOCATION",
                request.allocationId().toString(),
                Map.of("previousUserId", oldUserId.toString()),
                Map.of("newUserId", request.candidateUserId().toString(), "rebalancedBy", actor.email())
        );

        String message = String.format("Alokasi berhasil dialihkan kepada %s.", candidateName);
        return new ApplyBalanceSuggestionResponse(
                true,
                message,
                request.allocationId(),
                request.candidateUserId(),
                candidateName,
                taskIdToUpdate
        );
    }

    private record TaskMeta(UUID id, String title, String description, BigDecimal estimatedHours, LocalDate startDate, LocalDate dueDate) {}
    private record UserMeta(UUID id, String email, String fullName, String role) {}
    private record SkillMeta(UUID userId, String skillName, String proficiencyLevel) {}
    private record UserCapacitySummary(BigDecimal netCapacityHours, BigDecimal confirmedHours, BigDecimal availableHours, BigDecimal currentUtilPct) {}
    private record UserCapacitySummaryWithName(UUID userId, String fullName, String role, BigDecimal utilPct, BigDecimal availableHours) {}
    private record AllocationMeta(UUID id, UUID projectId, String projectCode, String projectName, UUID taskId, String taskTitle, BigDecimal hoursPerDay, LocalDate startDate, LocalDate endDate) {}
}

