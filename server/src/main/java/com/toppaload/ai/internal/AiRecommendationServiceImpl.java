package com.toppaload.ai.internal;

import com.toppaload.ai.AiRecommendationService;
import com.toppaload.ai.AssigneeRecommendationDto;
import com.toppaload.ai.TaskRecommendationResponse;
import com.toppaload.ai.WorkloadBalanceSuggestionDto;
import com.toppaload.capacity.CapacityCell;
import com.toppaload.capacity.CapacityQueryService;
import com.toppaload.capacity.Granularity;
import com.toppaload.capacity.HeatmapResponse;
import com.toppaload.capacity.UserHeatmapRow;
import com.toppaload.identity.SystemSettingsDto;
import com.toppaload.identity.SystemSettingsReader;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
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

    AiRecommendationServiceImpl(
            JdbcClient jdbc,
            CapacityQueryService capacityQueryService,
            SystemSettingsReader systemSettingsReader
    ) {
        this.jdbc = jdbc;
        this.capacityQueryService = capacityQueryService;
        this.systemSettingsReader = systemSettingsReader;
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

            if (utilPct.compareTo(overThreshold) >= 0) {
                overloaded.add(new UserCapacitySummaryWithName(row.userId(), row.fullName(), row.role(), utilPct));
            } else if (utilPct.compareTo(underThreshold) <= 0) {
                underutilized.add(new UserCapacitySummaryWithName(row.userId(), row.fullName(), row.role(), utilPct));
            }
        }

        List<WorkloadBalanceSuggestionDto> suggestions = new ArrayList<>();

        for (UserCapacitySummaryWithName over : overloaded) {
            for (UserCapacitySummaryWithName under : underutilized) {
                String text = String.format(
                        "Beban kerja %s mencapai %s%% (Overload). Pertimbangkan untuk mengalihkan alokasi atau task mendatang kepada %s yang saat ini berutilisasi %s%%.",
                        over.fullName(), over.utilPct(), under.fullName(), under.utilPct()
                );

                suggestions.add(new WorkloadBalanceSuggestionDto(
                        over.userId(),
                        over.fullName(),
                        over.utilPct(),
                        under.userId(),
                        under.fullName(),
                        under.utilPct(),
                        text,
                        under.role()
                ));
            }
        }

        return suggestions;
    }

    private record TaskMeta(UUID id, String title, String description, BigDecimal estimatedHours, LocalDate startDate, LocalDate dueDate) {}
    private record UserMeta(UUID id, String email, String fullName, String role) {}
    private record SkillMeta(UUID userId, String skillName, String proficiencyLevel) {}
    private record UserCapacitySummary(BigDecimal netCapacityHours, BigDecimal confirmedHours, BigDecimal availableHours, BigDecimal currentUtilPct) {}
    private record UserCapacitySummaryWithName(UUID userId, String fullName, String role, BigDecimal utilPct) {}
}

