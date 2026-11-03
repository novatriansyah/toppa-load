package com.toppaload.capacity.internal;

import com.toppaload.capacity.Granularity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
class JdbcCapacityQueryRepository {

    private final JdbcClient jdbc;

    JdbcCapacityQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record RawBucketRow(
        UUID userId,
        String fullName,
        String role,
        BigDecimal workHoursPerDay,
        LocalDate bucketStart,
        BigDecimal netCapacityHours,
        BigDecimal leaveHours,
        BigDecimal confirmedHours,
        BigDecimal tentativeHours,
        boolean hasLeaveConflict
    ) {}

    record RawDailyBreakdownRow(
        LocalDate workDate,
        int dayOfWeek,
        boolean isWorkday,
        boolean isHoliday,
        String holidayName,
        BigDecimal baseHours,
        BigDecimal leaveHours,
        BigDecimal netCapacityHours,
        BigDecimal confirmedHours,
        BigDecimal tentativeHours
    ) {}

    public List<RawBucketRow> queryBucketRows(
        LocalDate from,
        LocalDate to,
        Granularity granularity,
        UUID userId,
        UUID excludeAllocationId
    ) {
        String sql = """
            WITH calendar_days AS (
                SELECT gs::date AS work_date,
                       CASE CAST(:granularity AS text)
                           WHEN 'DAY'   THEN gs::date
                           WHEN 'MONTH' THEN date_trunc('month', gs)::date
                           ELSE              date_trunc('week', gs)::date
                       END AS bucket_start
                FROM generate_series(:from::date, :to::date, interval '1 day') AS gs
            ),
            holiday_dates AS (
                SELECT DISTINCT holiday_date
                FROM holidays
                WHERE deleted_at IS NULL
                  AND holiday_date BETWEEN :from::date AND :to::date
            ),
            daily_user_grid AS (
                SELECT u.id AS user_id,
                       u.full_name,
                       u.role,
                       cd.work_date,
                       cd.bucket_start,
                       (EXTRACT(ISODOW FROM cd.work_date)::smallint = ANY(u.work_days)) AS is_workday,
                       (hd.holiday_date IS NOT NULL) AS is_holiday,
                       CASE
                           WHEN (EXTRACT(ISODOW FROM cd.work_date)::smallint = ANY(u.work_days))
                                AND hd.holiday_date IS NULL
                           THEN u.work_hours_per_day
                           ELSE 0.00
                       END AS base_hours,
                       u.work_hours_per_day
                FROM users u
                CROSS JOIN calendar_days cd
                LEFT JOIN holiday_dates hd ON hd.holiday_date = cd.work_date
                WHERE u.active = TRUE
                  AND u.deleted_at IS NULL
                  AND (CAST(:userId AS uuid) IS NULL OR u.id = CAST(:userId AS uuid))
            ),
            daily_leaves AS (
                SELECT l.user_id,
                       gs::date AS work_date,
                       SUM(
                           CASE
                               WHEN l.portion = 'FULL' THEN 1.0
                               ELSE 0.5
                           END
                       ) AS leave_day_fraction
                FROM leaves l
                CROSS JOIN LATERAL generate_series(
                    GREATEST(l.start_date, :from::date),
                    LEAST(l.end_date, :to::date),
                    interval '1 day'
                ) AS gs
                WHERE l.deleted_at IS NULL
                  AND l.status = 'APPROVED'
                  AND l.start_date <= :to::date
                  AND l.end_date >= :from::date
                  AND (CAST(:userId AS uuid) IS NULL OR l.user_id = CAST(:userId AS uuid))
                GROUP BY l.user_id, gs::date
            ),
            daily_allocations AS (
                SELECT a.user_id,
                       gs::date AS work_date,
                       COALESCE(SUM(a.hours_per_day) FILTER (WHERE a.allocation_type = 'CONFIRMED'), 0.00) AS confirmed_raw,
                       COALESCE(SUM(a.hours_per_day) FILTER (WHERE a.allocation_type = 'TENTATIVE'), 0.00) AS tentative_raw
                FROM allocations a
                CROSS JOIN LATERAL generate_series(
                    GREATEST(a.start_date, :from::date),
                    LEAST(a.end_date, :to::date),
                    interval '1 day'
                ) AS gs
                WHERE a.deleted_at IS NULL
                  AND a.start_date <= :to::date
                  AND a.end_date >= :from::date
                  AND (CAST(:userId AS uuid) IS NULL OR a.user_id = CAST(:userId AS uuid))
                  AND (CAST(:excludeAllocationId AS uuid) IS NULL OR a.id != CAST(:excludeAllocationId AS uuid))
                GROUP BY a.user_id, gs::date
            ),
            daily_effective AS (
                SELECT g.user_id,
                       g.full_name,
                       g.role,
                       g.work_hours_per_day,
                       g.work_date,
                       g.bucket_start,
                       g.base_hours,
                       LEAST(g.base_hours, ROUND(COALESCE(dl.leave_day_fraction, 0) * g.work_hours_per_day, 2)) AS leave_hours,
                       GREATEST(0.00, g.base_hours - LEAST(g.base_hours, ROUND(COALESCE(dl.leave_day_fraction, 0) * g.work_hours_per_day, 2))) AS net_capacity_hours,
                       CASE WHEN g.is_workday AND NOT g.is_holiday THEN COALESCE(da.confirmed_raw, 0.00) ELSE 0.00 END AS confirmed_hours,
                       CASE WHEN g.is_workday AND NOT g.is_holiday THEN COALESCE(da.tentative_raw, 0.00) ELSE 0.00 END AS tentative_hours
                FROM daily_user_grid g
                LEFT JOIN daily_leaves dl ON dl.user_id = g.user_id AND dl.work_date = g.work_date
                LEFT JOIN daily_allocations da ON da.user_id = g.user_id AND da.work_date = g.work_date
            )
            SELECT user_id,
                   full_name,
                   role,
                   work_hours_per_day,
                   bucket_start,
                   COALESCE(SUM(net_capacity_hours), 0.00) AS net_capacity_hours,
                   COALESCE(SUM(leave_hours), 0.00)        AS leave_hours,
                   COALESCE(SUM(confirmed_hours), 0.00)    AS confirmed_hours,
                   COALESCE(SUM(tentative_hours), 0.00)    AS tentative_hours,
                   BOOL_OR(leave_hours > 0 AND confirmed_hours > net_capacity_hours) AS has_leave_conflict
            FROM daily_effective
            GROUP BY user_id, full_name, role, work_hours_per_day, bucket_start
            ORDER BY full_name ASC, bucket_start ASC
        """;

        return jdbc.sql(sql)
            .param("from", from)
            .param("to", to)
            .param("granularity", granularity.name())
            .param("userId", userId)
            .param("excludeAllocationId", excludeAllocationId)
            .query((rs, rowNum) -> new RawBucketRow(
                rs.getObject("user_id", UUID.class),
                rs.getString("full_name"),
                rs.getString("role"),
                rs.getBigDecimal("work_hours_per_day"),
                rs.getObject("bucket_start", LocalDate.class),
                rs.getBigDecimal("net_capacity_hours"),
                rs.getBigDecimal("leave_hours"),
                rs.getBigDecimal("confirmed_hours"),
                rs.getBigDecimal("tentative_hours"),
                rs.getBoolean("has_leave_conflict")
            ))
            .list();
    }

    public List<RawDailyBreakdownRow> queryDailyBreakdownRows(UUID userId, LocalDate from, LocalDate to) {
        String sql = """
            WITH calendar_days AS (
                SELECT gs::date AS work_date
                FROM generate_series(:from::date, :to::date, interval '1 day') AS gs
            ),
            holiday_dates AS (
                SELECT holiday_date, name
                FROM holidays
                WHERE deleted_at IS NULL
                  AND holiday_date BETWEEN :from::date AND :to::date
            ),
            daily_leaves AS (
                SELECT gs::date AS work_date,
                       SUM(CASE WHEN l.portion = 'FULL' THEN 1.0 ELSE 0.5 END) AS leave_fraction
                FROM leaves l
                CROSS JOIN LATERAL generate_series(
                    GREATEST(l.start_date, :from::date),
                    LEAST(l.end_date, :to::date),
                    interval '1 day'
                ) AS gs
                WHERE l.deleted_at IS NULL
                  AND l.status = 'APPROVED'
                  AND l.user_id = CAST(:userId AS uuid)
                  AND l.start_date <= :to::date
                  AND l.end_date >= :from::date
                GROUP BY gs::date
            ),
            daily_allocations AS (
                SELECT gs::date AS work_date,
                       COALESCE(SUM(a.hours_per_day) FILTER (WHERE a.allocation_type = 'CONFIRMED'), 0.00) AS confirmed_raw,
                       COALESCE(SUM(a.hours_per_day) FILTER (WHERE a.allocation_type = 'TENTATIVE'), 0.00) AS tentative_raw
                FROM allocations a
                CROSS JOIN LATERAL generate_series(
                    GREATEST(a.start_date, :from::date),
                    LEAST(a.end_date, :to::date),
                    interval '1 day'
                ) AS gs
                WHERE a.deleted_at IS NULL
                  AND a.user_id = CAST(:userId AS uuid)
                  AND a.start_date <= :to::date
                  AND a.end_date >= :from::date
                GROUP BY gs::date
            )
            SELECT cd.work_date,
                   EXTRACT(ISODOW FROM cd.work_date)::int AS day_of_week,
                   (EXTRACT(ISODOW FROM cd.work_date)::smallint = ANY(u.work_days)) AS is_workday,
                   (hd.holiday_date IS NOT NULL) AS is_holiday,
                   hd.name AS holiday_name,
                   CASE
                       WHEN (EXTRACT(ISODOW FROM cd.work_date)::smallint = ANY(u.work_days)) AND hd.holiday_date IS NULL
                       THEN u.work_hours_per_day
                       ELSE 0.00
                   END AS base_hours,
                   LEAST(
                       CASE WHEN (EXTRACT(ISODOW FROM cd.work_date)::smallint = ANY(u.work_days)) AND hd.holiday_date IS NULL THEN u.work_hours_per_day ELSE 0.00 END,
                       ROUND(COALESCE(dl.leave_fraction, 0) * u.work_hours_per_day, 2)
                   ) AS leave_hours,
                   GREATEST(
                       0.00,
                       (CASE WHEN (EXTRACT(ISODOW FROM cd.work_date)::smallint = ANY(u.work_days)) AND hd.holiday_date IS NULL THEN u.work_hours_per_day ELSE 0.00 END) -
                       LEAST(
                           CASE WHEN (EXTRACT(ISODOW FROM cd.work_date)::smallint = ANY(u.work_days)) AND hd.holiday_date IS NULL THEN u.work_hours_per_day ELSE 0.00 END,
                           ROUND(COALESCE(dl.leave_fraction, 0) * u.work_hours_per_day, 2)
                       )
                   ) AS net_capacity_hours,
                   CASE WHEN (EXTRACT(ISODOW FROM cd.work_date)::smallint = ANY(u.work_days)) AND hd.holiday_date IS NULL THEN COALESCE(da.confirmed_raw, 0.00) ELSE 0.00 END AS confirmed_hours,
                   CASE WHEN (EXTRACT(ISODOW FROM cd.work_date)::smallint = ANY(u.work_days)) AND hd.holiday_date IS NULL THEN COALESCE(da.tentative_raw, 0.00) ELSE 0.00 END AS tentative_hours
            FROM calendar_days cd
            CROSS JOIN users u
            LEFT JOIN holiday_dates hd ON hd.holiday_date = cd.work_date
            LEFT JOIN daily_leaves dl ON dl.work_date = cd.work_date
            LEFT JOIN daily_allocations da ON da.work_date = cd.work_date
            WHERE u.id = CAST(:userId AS uuid)
            ORDER BY cd.work_date ASC
        """;

        return jdbc.sql(sql)
            .param("from", from)
            .param("to", to)
            .param("userId", userId)
            .query((rs, rowNum) -> new RawDailyBreakdownRow(
                rs.getObject("work_date", LocalDate.class),
                rs.getInt("day_of_week"),
                rs.getBoolean("is_workday"),
                rs.getBoolean("is_holiday"),
                rs.getString("holiday_name"),
                rs.getBigDecimal("base_hours"),
                rs.getBigDecimal("leave_hours"),
                rs.getBigDecimal("net_capacity_hours"),
                rs.getBigDecimal("confirmed_hours"),
                rs.getBigDecimal("tentative_hours")
            ))
            .list();
    }
}
