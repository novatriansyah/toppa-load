package com.toppaload.capacity.internal;

import com.toppaload.allocation.AllocationSummary;
import com.toppaload.allocation.AllocationType;
import com.toppaload.capacity.AllocationPreviewRequest;
import com.toppaload.capacity.AllocationPreviewResponse;
import com.toppaload.capacity.CapacityCell;
import com.toppaload.capacity.CapacityQueryService;
import com.toppaload.capacity.DayBreakdownDto;
import com.toppaload.capacity.Granularity;
import com.toppaload.capacity.HeatmapResponse;
import com.toppaload.capacity.UserHeatmapRow;
import com.toppaload.capacity.UtilizationStatus;
import com.toppaload.leave.LeaveSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.toppaload.identity.SystemSettingsReader;

@Service
@Transactional(readOnly = true)
class CapacityQueryServiceImpl implements CapacityQueryService {

    private final JdbcCapacityQueryRepository queryRepository;
    private final JdbcClient jdbc;
    private final SystemSettingsReader settingsReader;

    CapacityQueryServiceImpl(JdbcCapacityQueryRepository queryRepository, JdbcClient jdbc, SystemSettingsReader settingsReader) {
        this.queryRepository = queryRepository;
        this.jdbc = jdbc;
        this.settingsReader = settingsReader;
    }

    @Override
    public HeatmapResponse getHeatmap(LocalDate from, LocalDate to, Granularity granularity, boolean includeTentative) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("From and to dates are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("To date cannot be before from date");
        }
        Granularity gran = granularity != null ? granularity : Granularity.WEEK;

        Thresholds thresholds = currentThresholds();

        List<JdbcCapacityQueryRepository.RawBucketRow> rawRows =
            queryRepository.queryBucketRows(from, to, gran, null, null);

        // Generate full list of distinct periods (sorted bucket starts)
        List<LocalDate> periods = generatePeriods(from, to, gran);

        // Group rows by userId
        Map<UUID, List<JdbcCapacityQueryRepository.RawBucketRow>> byUser = new LinkedHashMap<>();
        Map<UUID, UserInfo> userInfos = new LinkedHashMap<>();

        for (var r : rawRows) {
            byUser.computeIfAbsent(r.userId(), k -> new ArrayList<>()).add(r);
            userInfos.putIfAbsent(r.userId(), new UserInfo(r.userId(), r.fullName(), r.role(), r.workHoursPerDay()));
        }

        List<UserHeatmapRow> userRows = new ArrayList<>();
        for (var entry : userInfos.entrySet()) {
            UUID userId = entry.getKey();
            UserInfo info = entry.getValue();
            Map<LocalDate, JdbcCapacityQueryRepository.RawBucketRow> rowByBucket = byUser.getOrDefault(userId, Collections.emptyList())
                .stream()
                .collect(Collectors.toMap(JdbcCapacityQueryRepository.RawBucketRow::bucketStart, r -> r, (a, b) -> a));

            List<CapacityCell> cells = new ArrayList<>();
            for (LocalDate bucketStart : periods) {
                LocalDate bucketEnd = calculateBucketEnd(bucketStart, gran, to);
                var row = rowByBucket.get(bucketStart);
                if (row != null) {
                    cells.add(CapacityClassifier.buildCell(
                        bucketStart,
                        bucketEnd,
                        row.netCapacityHours(),
                        row.leaveHours(),
                        row.confirmedHours(),
                        row.tentativeHours(),
                        includeTentative,
                        row.hasLeaveConflict(),
                        thresholds.underutilized(),
                        thresholds.overloaded()
                    ));
                } else {
                    cells.add(new CapacityCell(
                        bucketStart,
                        bucketEnd,
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(1),
                        UtilizationStatus.NO_CAPACITY,
                        false
                    ));
                }
            }

            userRows.add(new UserHeatmapRow(
                info.userId(),
                info.fullName(),
                info.role(),
                info.workHoursPerDay(),
                cells
            ));
        }

        return new HeatmapResponse(from, to, gran, includeTentative, periods, userRows);
    }

    @Override
    public List<DayBreakdownDto> getUserBreakdown(UUID userId, LocalDate from, LocalDate to) {
        Thresholds thresholds = currentThresholds();
        var dailyRows = queryRepository.queryDailyBreakdownRows(userId, from, to);

        // Fetch allocations and leaves for this user in range
        List<AllocationSummary> userAllocations = queryUserAllocations(userId, from, to);
        List<LeaveSummary> userLeaves = queryUserLeaves(userId, from, to);

        List<DayBreakdownDto> result = new ArrayList<>();
        for (var dr : dailyRows) {
            LocalDate d = dr.workDate();
            List<AllocationSummary> activeAllocs = userAllocations.stream()
                .filter(a -> !d.isBefore(a.startDate()) && !d.isAfter(a.endDate()))
                .toList();
            List<LeaveSummary> activeLeaves = userLeaves.stream()
                .filter(l -> !d.isBefore(l.startDate()) && !d.isAfter(l.endDate()))
                .toList();

            boolean dayHasLeaveConflict = dr.leaveHours().compareTo(BigDecimal.ZERO) > 0
                && dr.confirmedHours().compareTo(dr.netCapacityHours()) > 0;

            var classification = CapacityClassifier.classify(
                dr.netCapacityHours(),
                dr.confirmedHours(),
                dr.tentativeHours(),
                false,
                dayHasLeaveConflict,
                thresholds.underutilized(),
                thresholds.overloaded()
            );

            result.add(new DayBreakdownDto(
                d,
                dr.dayOfWeek(),
                dr.isWorkday(),
                dr.isHoliday(),
                dr.holidayName(),
                dr.baseHours(),
                dr.leaveHours(),
                dr.netCapacityHours(),
                dr.confirmedHours(),
                dr.tentativeHours(),
                classification.status(),
                classification.hasLeaveConflict(),
                activeAllocs,
                activeLeaves
            ));
        }

        return result;
    }

    @Override
    public AllocationPreviewResponse previewAllocation(AllocationPreviewRequest request) {
        Thresholds thresholds = currentThresholds();
        LocalDate from = request.startDate();
        LocalDate to = request.endDate();
        UUID userId = request.userId();
        Granularity gran = Granularity.WEEK;

        // Query before
        List<JdbcCapacityQueryRepository.RawBucketRow> beforeRows =
            queryRepository.queryBucketRows(from, to, gran, userId, request.excludeAllocationId());

        List<CapacityCell> beforeCells = new ArrayList<>();
        for (var r : beforeRows) {
            LocalDate bucketEnd = calculateBucketEnd(r.bucketStart(), gran, to);
            beforeCells.add(CapacityClassifier.buildCell(
                r.bucketStart(),
                bucketEnd,
                r.netCapacityHours(),
                r.leaveHours(),
                r.confirmedHours(),
                r.tentativeHours(),
                false,
                r.hasLeaveConflict(),
                thresholds.underutilized(),
                thresholds.overloaded()
            ));
        }

        // Compute simulated after by breaking down affected days
        var dailyRows = queryRepository.queryDailyBreakdownRows(userId, from, to);
        AllocationType type = request.allocationType() != null ? request.allocationType() : AllocationType.CONFIRMED;

        Map<LocalDate, BucketAccumulator> bucketAcc = new LinkedHashMap<>();
        for (var dr : dailyRows) {
            LocalDate d = dr.workDate();
            if (d.isBefore(from) || d.isAfter(to)) continue;

            LocalDate bucketStart = d.minusDays(d.getDayOfWeek().getValue() - 1); // Monday of week
            BucketAccumulator acc = bucketAcc.computeIfAbsent(bucketStart, k -> new BucketAccumulator());

            BigDecimal addConf = BigDecimal.ZERO;
            BigDecimal addTent = BigDecimal.ZERO;
            if (dr.isWorkday() && !dr.isHoliday()) {
                if (type == AllocationType.CONFIRMED) {
                    addConf = request.hoursPerDay();
                } else {
                    addTent = request.hoursPerDay();
                }
            }

            BigDecimal newConf = dr.confirmedHours().add(addConf);
            BigDecimal newTent = dr.tentativeHours().add(addTent);

            acc.netCapacity = acc.netCapacity.add(dr.netCapacityHours());
            acc.leave = acc.leave.add(dr.leaveHours());
            acc.confirmed = acc.confirmed.add(newConf);
            acc.tentative = acc.tentative.add(newTent);
            if (dr.leaveHours().compareTo(BigDecimal.ZERO) > 0 && newConf.compareTo(dr.netCapacityHours()) > 0) {
                acc.hasLeaveConflict = true;
            }
        }

        List<CapacityCell> afterCells = new ArrayList<>();
        boolean introducesOverload = false;
        boolean introducesLeaveConflict = false;

        Map<LocalDate, CapacityCell> beforeMap = beforeCells.stream()
            .collect(Collectors.toMap(CapacityCell::bucketStart, c -> c, (a, b) -> a));

        for (var entry : bucketAcc.entrySet()) {
            LocalDate bStart = entry.getKey();
            LocalDate bEnd = calculateBucketEnd(bStart, gran, to);
            BucketAccumulator acc = entry.getValue();

            CapacityCell cell = CapacityClassifier.buildCell(
                bStart,
                bEnd,
                acc.netCapacity,
                acc.leave,
                acc.confirmed,
                acc.tentative,
                false,
                acc.hasLeaveConflict,
                thresholds.underutilized(),
                thresholds.overloaded()
            );
            afterCells.add(cell);

            CapacityCell beforeCell = beforeMap.get(bStart);
            if (cell.status() == UtilizationStatus.OVERLOADED && (beforeCell == null || beforeCell.status() != UtilizationStatus.OVERLOADED)) {
                introducesOverload = true;
            }
            if (cell.hasLeaveConflict() && (beforeCell == null || !beforeCell.hasLeaveConflict())) {
                introducesLeaveConflict = true;
            }
        }

        return new AllocationPreviewResponse(
            beforeCells,
            afterCells,
            introducesOverload,
            introducesLeaveConflict
        );
    }

    private static class BucketAccumulator {
        BigDecimal netCapacity = BigDecimal.ZERO;
        BigDecimal leave = BigDecimal.ZERO;
        BigDecimal confirmed = BigDecimal.ZERO;
        BigDecimal tentative = BigDecimal.ZERO;
        boolean hasLeaveConflict = false;
    }

    private record UserInfo(UUID userId, String fullName, String role, BigDecimal workHoursPerDay) {}

    private record Thresholds(BigDecimal underutilized, BigDecimal overloaded) {}

    private Thresholds currentThresholds() {
        try {
            var s = settingsReader.currentSettings();
            return new Thresholds(s.underutilizedThresholdPct(), s.overloadedThresholdPct());
        } catch (Exception e) {
            return new Thresholds(
                    CapacityClassifier.DEFAULT_UNDERUTILIZED_THRESHOLD,
                    CapacityClassifier.DEFAULT_OVERLOADED_THRESHOLD
            );
        }
    }

    private List<LocalDate> generatePeriods(LocalDate from, LocalDate to, Granularity granularity) {
        List<LocalDate> periods = new ArrayList<>();
        LocalDate curr = from;
        while (!curr.isAfter(to)) {
            LocalDate bucketStart;
            switch (granularity) {
                case DAY -> bucketStart = curr;
                case MONTH -> bucketStart = curr.withDayOfMonth(1);
                default -> bucketStart = curr.minusDays(curr.getDayOfWeek().getValue() - 1);
            }
            if (!periods.contains(bucketStart)) {
                periods.add(bucketStart);
            }
            curr = curr.plusDays(1);
        }
        Collections.sort(periods);
        return periods;
    }

    private LocalDate calculateBucketEnd(LocalDate bucketStart, Granularity granularity, LocalDate periodEnd) {
        LocalDate calculatedEnd = switch (granularity) {
            case DAY -> bucketStart;
            case MONTH -> bucketStart.plusMonths(1).minusDays(1);
            default -> bucketStart.plusDays(6);
        };
        return calculatedEnd;
    }

    private List<AllocationSummary> queryUserAllocations(UUID userId, LocalDate from, LocalDate to) {
        String sql = """
            SELECT a.id, a.user_id, u.full_name AS user_name,
                   a.project_id, p.code AS project_code, p.name AS project_name,
                   a.task_id, t.title AS task_title,
                   a.start_date, a.end_date, a.hours_per_day, a.allocation_type,
                   a.notes, a.created_by, a.created_at, a.updated_at
            FROM allocations a
            JOIN users u ON u.id = a.user_id
            JOIN projects p ON p.id = a.project_id
            LEFT JOIN tasks t ON t.id = a.task_id
            WHERE a.deleted_at IS NULL
              AND a.user_id = CAST(:userId AS uuid)
              AND a.start_date <= :to::date
              AND a.end_date >= :from::date
            ORDER BY a.start_date ASC
        """;

        return jdbc.sql(sql)
            .param("userId", userId)
            .param("from", from)
            .param("to", to)
            .query((rs, rowNum) -> new AllocationSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("user_name"),
                rs.getObject("project_id", UUID.class),
                rs.getString("project_code"),
                rs.getString("project_name"),
                rs.getObject("task_id", UUID.class),
                rs.getString("task_title"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getBigDecimal("hours_per_day"),
                AllocationType.valueOf(rs.getString("allocation_type")),
                rs.getString("notes"),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            ))
            .list();
    }

    private List<LeaveSummary> queryUserLeaves(UUID userId, LocalDate from, LocalDate to) {
        String sql = """
            SELECT l.id, l.user_id, u.email AS user_email, u.full_name,
                   l.leave_type, l.portion, l.start_date, l.end_date,
                   l.status, l.reason AS notes, l.created_at
            FROM leaves l
            JOIN users u ON u.id = l.user_id
            WHERE l.deleted_at IS NULL
              AND l.user_id = CAST(:userId AS uuid)
              AND l.start_date <= :to::date
              AND l.end_date >= :from::date
            ORDER BY l.start_date ASC
        """;

        return jdbc.sql(sql)
            .param("userId", userId)
            .param("from", from)
            .param("to", to)
            .query((rs, rowNum) -> new LeaveSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("user_email"),
                rs.getString("full_name"),
                com.toppaload.leave.LeaveType.valueOf(rs.getString("leave_type")),
                com.toppaload.leave.LeavePortion.valueOf(rs.getString("portion")),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getString("status"),
                rs.getString("notes"),
                rs.getTimestamp("created_at").toInstant()
            ))
            .list();
    }
}

