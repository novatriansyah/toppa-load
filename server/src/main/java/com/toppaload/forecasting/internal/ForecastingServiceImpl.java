package com.toppaload.forecasting.internal;

import com.toppaload.audit.AuditRecorder;
import com.toppaload.capacity.CapacityCell;
import com.toppaload.capacity.CapacityQueryService;
import com.toppaload.capacity.Granularity;
import com.toppaload.capacity.HeatmapResponse;
import com.toppaload.capacity.UserHeatmapRow;
import com.toppaload.forecasting.AddScenarioAllocationRequest;
import com.toppaload.forecasting.CreateScenarioRequest;
import com.toppaload.forecasting.ForecastingService;
import com.toppaload.forecasting.ScenarioAllocationDto;
import com.toppaload.forecasting.ScenarioDto;
import com.toppaload.forecasting.SimulationResultDto;
import com.toppaload.forecasting.UserSimulationDeltaDto;
import com.toppaload.identity.SystemSettingsDto;
import com.toppaload.identity.SystemSettingsReader;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class ForecastingServiceImpl implements ForecastingService {

    private final JdbcClient jdbc;
    private final CapacityQueryService capacityQueryService;
    private final SystemSettingsReader systemSettingsReader;
    private final AuditRecorder auditRecorder;

    ForecastingServiceImpl(
            JdbcClient jdbc,
            CapacityQueryService capacityQueryService,
            SystemSettingsReader systemSettingsReader,
            AuditRecorder auditRecorder
    ) {
        this.jdbc = jdbc;
        this.capacityQueryService = capacityQueryService;
        this.systemSettingsReader = systemSettingsReader;
        this.auditRecorder = auditRecorder;
    }

    @Override
    public List<ScenarioDto> listScenarios() {
        return jdbc.sql("""
            SELECT s.id, s.name, s.description, s.created_by, u.full_name AS created_by_name, s.status,
                   (SELECT COUNT(*) FROM scenario_allocations sa WHERE sa.scenario_id = s.id) AS allocations_count,
                   s.created_at, s.updated_at
            FROM scenarios s
            LEFT JOIN users u ON u.id = s.created_by
            ORDER BY s.created_at DESC
        """)
        .query((rs, rowNum) -> new ScenarioDto(
                (UUID) rs.getObject("id"),
                rs.getString("name"),
                rs.getString("description"),
                (UUID) rs.getObject("created_by"),
                rs.getString("created_by_name"),
                rs.getString("status"),
                rs.getInt("allocations_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        ))
        .list();
    }

    @Override
    @Transactional
    public ScenarioDto createScenario(UUID creatorId, CreateScenarioRequest request) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO scenarios (id, name, description, created_by, status)
            VALUES (:id, :name, :description, :creatorId, 'DRAFT')
        """)
        .param("id", id)
        .param("name", request.name().trim())
        .param("description", request.description())
        .param("creatorId", creatorId)
        .update();

        return getScenario(id);
    }

    @Override
    public ScenarioDto getScenario(UUID id) {
        return jdbc.sql("""
            SELECT s.id, s.name, s.description, s.created_by, u.full_name AS created_by_name, s.status,
                   (SELECT COUNT(*) FROM scenario_allocations sa WHERE sa.scenario_id = s.id) AS allocations_count,
                   s.created_at, s.updated_at
            FROM scenarios s
            LEFT JOIN users u ON u.id = s.created_by
            WHERE s.id = :id
        """)
        .param("id", id)
        .query((rs, rowNum) -> new ScenarioDto(
                (UUID) rs.getObject("id"),
                rs.getString("name"),
                rs.getString("description"),
                (UUID) rs.getObject("created_by"),
                rs.getString("created_by_name"),
                rs.getString("status"),
                rs.getInt("allocations_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        ))
        .optional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skenario tidak ditemukan: " + id));
    }

    @Override
    public List<ScenarioAllocationDto> getScenarioAllocations(UUID scenarioId) {
        return jdbc.sql("""
            SELECT sa.id, sa.scenario_id, sa.user_id, u.full_name AS user_name, sa.project_id, sa.project_name,
                   sa.start_date, sa.end_date, sa.hours_per_day, sa.notes
            FROM scenario_allocations sa
            JOIN users u ON u.id = sa.user_id
            WHERE sa.scenario_id = :scenarioId
            ORDER BY sa.start_date ASC
        """)
        .param("scenarioId", scenarioId)
        .query((rs, rowNum) -> new ScenarioAllocationDto(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("scenario_id"),
                (UUID) rs.getObject("user_id"),
                rs.getString("user_name"),
                (UUID) rs.getObject("project_id"),
                rs.getString("project_name"),
                rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate(),
                rs.getBigDecimal("hours_per_day"),
                rs.getString("notes")
        ))
        .list();
    }

    @Override
    @Transactional
    public ScenarioAllocationDto addScenarioAllocation(UUID scenarioId, AddScenarioAllocationRequest request) {
        getScenario(scenarioId); // Validate scenario exists

        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tanggal selesai tidak boleh sebelum tanggal mulai.");
        }

        UUID id = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO scenario_allocations (id, scenario_id, user_id, project_id, project_name, start_date, end_date, hours_per_day, notes)
            VALUES (:id, :scenarioId, :userId, :projectId, :projectName, :startDate, :endDate, :hoursPerDay, :notes)
        """)
        .param("id", id)
        .param("scenarioId", scenarioId)
        .param("userId", request.userId())
        .param("projectId", request.projectId())
        .param("projectName", request.projectName().trim())
        .param("startDate", request.startDate())
        .param("endDate", request.endDate())
        .param("hoursPerDay", request.hoursPerDay())
        .param("notes", request.notes())
        .update();

        jdbc.sql("UPDATE scenarios SET updated_at = NOW() WHERE id = :id").param("id", scenarioId).update();

        return getScenarioAllocations(scenarioId).stream()
                .filter(a -> a.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Override
    @Transactional
    public void deleteScenarioAllocation(UUID scenarioId, UUID allocationId) {
        int deleted = jdbc.sql("""
            DELETE FROM scenario_allocations
            WHERE id = :allocId AND scenario_id = :scenarioId
        """)
        .param("allocId", allocationId)
        .param("scenarioId", scenarioId)
        .update();

        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alokasi skenario tidak ditemukan: " + allocationId);
        }

        jdbc.sql("UPDATE scenarios SET updated_at = NOW() WHERE id = :id").param("id", scenarioId).update();
    }

    @Override
    @Transactional
    public void deleteScenario(UUID scenarioId) {
        jdbc.sql("DELETE FROM scenario_allocations WHERE scenario_id = :id")
                .param("id", scenarioId)
                .update();

        int deleted = jdbc.sql("DELETE FROM scenarios WHERE id = :id")
                .param("id", scenarioId)
                .update();

        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skenario tidak ditemukan: " + scenarioId);
        }
    }

    @Override
    @Transactional
    public SimulationResultDto simulateScenario(UUID scenarioId, LocalDate startDate, LocalDate endDate) {
        ScenarioDto scenario = getScenario(scenarioId);
        List<ScenarioAllocationDto> draftAllocations = getScenarioAllocations(scenarioId);

        LocalDate start;
        LocalDate end;

        if (startDate != null && endDate != null) {
            start = startDate;
            end = endDate;
        } else if (!draftAllocations.isEmpty()) {
            LocalDate minAllocStart = draftAllocations.stream()
                    .map(ScenarioAllocationDto::startDate)
                    .min(Comparator.naturalOrder())
                    .orElse(LocalDate.now());
            LocalDate maxAllocEnd = draftAllocations.stream()
                    .map(ScenarioAllocationDto::endDate)
                    .max(Comparator.naturalOrder())
                    .orElse(minAllocStart.plusWeeks(4));

            start = minAllocStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            end = maxAllocEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        } else {
            start = (startDate != null) ? startDate : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            end = (endDate != null) ? endDate : start.plusWeeks(8).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        }

        SystemSettingsDto settings = systemSettingsReader.currentSettings();
        BigDecimal underThreshold = settings.underutilizedThresholdPct();
        BigDecimal overThreshold = settings.overloadedThresholdPct();

        HeatmapResponse baselineHeatmap = capacityQueryService.getHeatmap(start, end, Granularity.WEEK, false);

        // Sum delta hours per user from scenario allocations intersecting [start, end]
        Map<UUID, BigDecimal> userDeltasMap = new HashMap<>();
        for (ScenarioAllocationDto alloc : draftAllocations) {
            LocalDate interStart = alloc.startDate().isBefore(start) ? start : alloc.startDate();
            LocalDate interEnd = alloc.endDate().isAfter(end) ? end : alloc.endDate();

            if (!interStart.isAfter(interEnd)) {
                long workingDays = interStart.datesUntil(interEnd.plusDays(1))
                        .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY)
                        .count();
                BigDecimal delta = alloc.hoursPerDay().multiply(BigDecimal.valueOf(workingDays));
                userDeltasMap.merge(alloc.userId(), delta, BigDecimal::add);
            }
        }

        List<UserSimulationDeltaDto> userDeltas = new ArrayList<>();
        BigDecimal totalCapacity = BigDecimal.ZERO;
        BigDecimal totalBaselineAlloc = BigDecimal.ZERO;
        BigDecimal totalSimulatedAlloc = BigDecimal.ZERO;
        int baselineOverloadedCount = 0;
        int simulatedOverloadedCount = 0;

        for (UserHeatmapRow row : baselineHeatmap.rows()) {
            BigDecimal capacityHours = BigDecimal.ZERO;
            BigDecimal baselineHours = BigDecimal.ZERO;

            for (CapacityCell cell : row.cells()) {
                capacityHours = capacityHours.add(cell.netCapacityHours() != null ? cell.netCapacityHours() : BigDecimal.ZERO);
                baselineHours = baselineHours.add(cell.confirmedHours() != null ? cell.confirmedHours() : BigDecimal.ZERO);
            }

            BigDecimal deltaHours = userDeltasMap.getOrDefault(row.userId(), BigDecimal.ZERO);
            BigDecimal totalSimHours = baselineHours.add(deltaHours);

            BigDecimal baselineUtilPct = capacityHours.compareTo(BigDecimal.ZERO) > 0
                    ? baselineHours.multiply(BigDecimal.valueOf(100)).divide(capacityHours, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal simulatedUtilPct = capacityHours.compareTo(BigDecimal.ZERO) > 0
                    ? totalSimHours.multiply(BigDecimal.valueOf(100)).divide(capacityHours, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String baseStatus = classifyStatus(baselineUtilPct, underThreshold, overThreshold);
            String simStatus = classifyStatus(simulatedUtilPct, underThreshold, overThreshold);

            if ("OVERLOADED".equals(baseStatus)) baselineOverloadedCount++;
            if ("OVERLOADED".equals(simStatus)) simulatedOverloadedCount++;

            boolean isNewlyOverloaded = !"OVERLOADED".equals(baseStatus) && "OVERLOADED".equals(simStatus);

            userDeltas.add(new UserSimulationDeltaDto(
                    row.userId(),
                    row.fullName(),
                    capacityHours.setScale(1, RoundingMode.HALF_UP),
                    baselineHours.setScale(1, RoundingMode.HALF_UP),
                    deltaHours.setScale(1, RoundingMode.HALF_UP),
                    totalSimHours.setScale(1, RoundingMode.HALF_UP),
                    baselineUtilPct,
                    simulatedUtilPct,
                    baseStatus,
                    simStatus,
                    isNewlyOverloaded
            ));

            totalCapacity = totalCapacity.add(capacityHours);
            totalBaselineAlloc = totalBaselineAlloc.add(baselineHours);
            totalSimulatedAlloc = totalSimulatedAlloc.add(totalSimHours);
        }

        BigDecimal baselineOverallPct = totalCapacity.compareTo(BigDecimal.ZERO) > 0
                ? totalBaselineAlloc.multiply(BigDecimal.valueOf(100)).divide(totalCapacity, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal simulatedOverallPct = totalCapacity.compareTo(BigDecimal.ZERO) > 0
                ? totalSimulatedAlloc.multiply(BigDecimal.valueOf(100)).divide(totalCapacity, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        if ("DRAFT".equals(scenario.status())) {
            jdbc.sql("UPDATE scenarios SET status = 'SIMULATED', updated_at = NOW() WHERE id = :id")
                    .param("id", scenarioId)
                    .update();
        }

        return new SimulationResultDto(
                scenarioId,
                scenario.name(),
                start,
                end,
                baselineOverallPct,
                simulatedOverallPct,
                baselineOverloadedCount,
                simulatedOverloadedCount,
                userDeltas
        );
    }

    @Override
    @Transactional
    public int promoteScenario(UUID scenarioId, UUID promoterId) {
        ScenarioDto scenario = getScenario(scenarioId);
        List<ScenarioAllocationDto> draftAllocations = getScenarioAllocations(scenarioId);

        if (draftAllocations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skenario tidak memiliki alokasi untuk dipromosikan.");
        }

        int promotedCount = 0;
        for (ScenarioAllocationDto draft : draftAllocations) {
            UUID projectId = draft.projectId();
            if (projectId == null) {
                // Find or create project
                projectId = jdbc.sql("SELECT id FROM projects WHERE LOWER(name) = LOWER(:name) AND deleted_at IS NULL LIMIT 1")
                        .param("name", draft.projectName().trim())
                        .query(UUID.class)
                        .optional()
                        .orElseGet(() -> {
                            UUID newProjId = UUID.randomUUID();
                            String code = "PRJ-" + newProjId.toString().substring(0, 8).toUpperCase();
                            jdbc.sql("""
                                INSERT INTO projects (id, code, name, description, status, start_date, end_date, owner_id)
                                VALUES (:id, :code, :name, 'Dibuat otomatis dari promosi simulasi', 'ACTIVE', :start, :end, :ownerId)
                            """)
                            .param("id", newProjId)
                            .param("code", code)
                            .param("name", draft.projectName().trim())
                            .param("start", draft.startDate())
                            .param("end", draft.endDate())
                            .param("ownerId", promoterId)
                            .update();
                            return newProjId;
                        });
            }

            jdbc.sql("""
                INSERT INTO allocations (id, project_id, user_id, start_date, end_date, hours_per_day, allocation_type, notes, created_by)
                VALUES (gen_random_uuid(), :projId, :userId, :start, :end, :hours, 'CONFIRMED', :notes, :promoterId)
            """)
            .param("projId", projectId)
            .param("userId", draft.userId())
            .param("start", draft.startDate())
            .param("end", draft.endDate())
            .param("hours", draft.hoursPerDay())
            .param("notes", draft.notes())
            .param("promoterId", promoterId)
            .update();

            promotedCount++;
        }

        jdbc.sql("UPDATE scenarios SET status = 'PROMOTED', updated_at = NOW() WHERE id = :id")
                .param("id", scenarioId)
                .update();

        // Audit log
        Map<String, Object> details = new HashMap<>();
        details.put("scenarioId", scenarioId);
        details.put("scenarioName", scenario.name());
        details.put("promotedAllocationsCount", promotedCount);

        auditRecorder.record(
                promoterId,
                "promoter",
                "PROMOTE",
                "SCENARIO",
                scenarioId.toString(),
                Map.of("status", scenario.status()),
                details
        );

        return promotedCount;
    }

    private String classifyStatus(BigDecimal pct, BigDecimal under, BigDecimal over) {
        if (pct.compareTo(over) >= 0) return "OVERLOADED";
        if (pct.compareTo(under) <= 0) return "UNDERUTILIZED";
        return "OPTIMAL";
    }
}

