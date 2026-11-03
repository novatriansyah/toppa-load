package com.toppaload.leave.internal;

import com.toppaload.identity.UserDirectory;
import com.toppaload.identity.UserSummary;
import com.toppaload.leave.LeaveBalanceDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
class LeaveBalanceService {

    private final JdbcClient jdbc;
    private final UserDirectory userDirectory;

    LeaveBalanceService(JdbcClient jdbc, UserDirectory userDirectory) {
        this.jdbc = jdbc;
        this.userDirectory = userDirectory;
    }

    @Transactional
    public LeaveBalanceDto getBalance(UUID userId, int year) {
        ensureBalanceExists(userId, year);

        return jdbc.sql("""
            SELECT b.id, b.user_id, u.full_name as user_full_name, b.year,
                   b.total_allowance_days, b.used_days, b.remaining_days
            FROM leave_balances b
            JOIN users u ON u.id = b.user_id
            WHERE b.user_id = :userId AND b.year = :year
        """)
        .param("userId", userId)
        .param("year", year)
        .query((rs, rowNum) -> new LeaveBalanceDto(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("user_id")),
                rs.getString("user_full_name"),
                rs.getInt("year"),
                rs.getBigDecimal("total_allowance_days"),
                rs.getBigDecimal("used_days"),
                rs.getBigDecimal("remaining_days")
        ))
        .single();
    }

    private void ensureBalanceExists(UUID userId, int year) {
        jdbc.sql("""
            INSERT INTO leave_balances (id, user_id, year, total_allowance_days, used_days, remaining_days)
            VALUES (gen_random_uuid(), :userId, :year, 12.0, 0.0, 12.0)
            ON CONFLICT (user_id, year) DO NOTHING
        """)
        .param("userId", userId)
        .param("year", year)
        .update();
    }

    @Transactional
    public void recalculateBalance(UUID userId, int year) {
        ensureBalanceExists(userId, year);

        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        // Sum up approved annual leave days
        BigDecimal usedDays = jdbc.sql("""
            SELECT COALESCE(SUM(
                CASE
                    WHEN portion = 'FULL' THEN (LEAST(end_date, :yearEnd) - GREATEST(start_date, :yearStart) + 1)
                    ELSE 0.5
                END
            ), 0.0) AS used_days
            FROM leaves
            WHERE user_id = :userId
              AND leave_type = 'ANNUAL'
              AND status = 'APPROVED'
              AND deleted_at IS NULL
              AND end_date >= :yearStart AND start_date <= :yearEnd
        """)
        .param("userId", userId)
        .param("yearStart", yearStart)
        .param("yearEnd", yearEnd)
        .query(rs -> {
            rs.next();
            return rs.getBigDecimal("used_days");
        });

        jdbc.sql("""
            UPDATE leave_balances
            SET used_days = :usedDays,
                remaining_days = GREATEST(0.0, total_allowance_days - :usedDays),
                updated_at = NOW()
            WHERE user_id = :userId AND year = :year
        """)
        .param("userId", userId)
        .param("year", year)
        .param("usedDays", usedDays)
        .update();
    }
}

