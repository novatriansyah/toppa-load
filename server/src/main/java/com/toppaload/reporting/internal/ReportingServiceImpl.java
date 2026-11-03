package com.toppaload.reporting.internal;

import com.toppaload.capacity.CapacityCell;
import com.toppaload.capacity.CapacityQueryService;
import com.toppaload.capacity.Granularity;
import com.toppaload.capacity.HeatmapResponse;
import com.toppaload.capacity.UserHeatmapRow;
import com.toppaload.capacity.UtilizationStatus;
import com.toppaload.identity.SystemSettingsDto;
import com.toppaload.identity.SystemSettingsReader;
import com.toppaload.reporting.MemberUtilizationDto;
import com.toppaload.reporting.ReportingService;
import com.toppaload.reporting.UtilizationSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class ReportingServiceImpl implements ReportingService {

    private static final Logger log = LoggerFactory.getLogger(ReportingServiceImpl.class);

    private final CapacityQueryService capacityQueryService;
    private final SystemSettingsReader systemSettingsReader;
    private final JdbcClient jdbc;
    private final JavaMailSender mailSender;

    ReportingServiceImpl(
            CapacityQueryService capacityQueryService,
            SystemSettingsReader systemSettingsReader,
            JdbcClient jdbc,
            @Autowired(required = false) JavaMailSender mailSender
    ) {
        this.capacityQueryService = capacityQueryService;
        this.systemSettingsReader = systemSettingsReader;
        this.jdbc = jdbc;
        this.mailSender = mailSender;
    }

    @Override
    public UtilizationSummaryDto getUtilizationSummary(LocalDate startDate, LocalDate endDate) {
        LocalDate start = (startDate != null) ? startDate : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = (endDate != null) ? endDate : start.plusWeeks(4).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));

        SystemSettingsDto settings = systemSettingsReader.currentSettings();
        BigDecimal underThreshold = settings.underutilizedThresholdPct();
        BigDecimal overThreshold = settings.overloadedThresholdPct();

        HeatmapResponse heatmap = capacityQueryService.getHeatmap(start, end, Granularity.WEEK, false);

        // Fetch user email & work_hours_per_day
        Map<UUID, UserMeta> userMetaMap = jdbc.sql("""
            SELECT id, email, work_hours_per_day, array_length(work_days, 1) AS days_count
            FROM users
            WHERE active = TRUE AND deleted_at IS NULL
        """)
        .query((rs, rowNum) -> {
            BigDecimal dailyHours = rs.getBigDecimal("work_hours_per_day");
            int days = rs.getInt("days_count");
            if (days <= 0) days = 5;
            BigDecimal weeklyCap = dailyHours != null ? dailyHours.multiply(BigDecimal.valueOf(days)) : BigDecimal.valueOf(40);
            return new UserMeta((UUID) rs.getObject("id"), rs.getString("email"), weeklyCap);
        })
        .list()
        .stream()
        .collect(java.util.stream.Collectors.toMap(UserMeta::userId, u -> u));

        // Fetch actual timesheet hours grouped by user
        Map<UUID, BigDecimal> actualHoursMap = jdbc.sql("""
            SELECT user_id, COALESCE(SUM(hours), 0) AS total_hours
            FROM time_entries
            WHERE entry_date BETWEEN :start AND :end
              AND deleted_at IS NULL
            GROUP BY user_id
        """)
        .param("start", start)
        .param("end", end)
        .query((rs, rowNum) -> Map.entry(
                (UUID) rs.getObject("user_id"),
                rs.getBigDecimal("total_hours")
        ))
        .list()
        .stream()
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<MemberUtilizationDto> members = new ArrayList<>();
        BigDecimal totalCapacity = BigDecimal.ZERO;
        BigDecimal totalAllocated = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        int overloadedMembersCount = 0;

        for (UserHeatmapRow row : heatmap.rows()) {
            UserMeta meta = userMetaMap.get(row.userId());
            String email = (meta != null) ? meta.email() : "";
            BigDecimal weeklyCap = (meta != null) ? meta.weeklyCapacity() : BigDecimal.valueOf(40);

            BigDecimal memberNetCap = BigDecimal.ZERO;
            BigDecimal memberAllocated = BigDecimal.ZERO;
            int overloadWeeks = 0;

            for (CapacityCell cell : row.cells()) {
                memberNetCap = memberNetCap.add(cell.netCapacityHours() != null ? cell.netCapacityHours() : BigDecimal.ZERO);
                memberAllocated = memberAllocated.add(cell.confirmedHours() != null ? cell.confirmedHours() : BigDecimal.ZERO);
                if (cell.status() == UtilizationStatus.OVERLOADED
                        || (cell.utilizationPct() != null && cell.utilizationPct().compareTo(overThreshold) >= 0)) {
                    overloadWeeks++;
                }
            }

            BigDecimal memberActual = actualHoursMap.getOrDefault(row.userId(), BigDecimal.ZERO);

            BigDecimal memberUtilPct;
            if (memberNetCap.compareTo(BigDecimal.ZERO) > 0) {
                memberUtilPct = memberAllocated.multiply(BigDecimal.valueOf(100))
                        .divide(memberNetCap, 1, RoundingMode.HALF_UP);
            } else {
                memberUtilPct = BigDecimal.ZERO;
            }

            String status;
            if (memberUtilPct.compareTo(overThreshold) >= 0) {
                status = "OVERLOADED";
                overloadedMembersCount++;
            } else if (memberUtilPct.compareTo(underThreshold) <= 0) {
                status = "UNDERUTILIZED";
            } else {
                status = "OPTIMAL";
            }

            members.add(new MemberUtilizationDto(
                    row.userId(),
                    row.fullName(),
                    email,
                    row.role(),
                    weeklyCap,
                    memberNetCap.setScale(1, RoundingMode.HALF_UP),
                    memberAllocated.setScale(1, RoundingMode.HALF_UP),
                    memberActual.setScale(1, RoundingMode.HALF_UP),
                    memberUtilPct,
                    overloadWeeks,
                    status
            ));

            totalCapacity = totalCapacity.add(memberNetCap);
            totalAllocated = totalAllocated.add(memberAllocated);
            totalActual = totalActual.add(memberActual);
        }

        BigDecimal overallUtilPct;
        if (totalCapacity.compareTo(BigDecimal.ZERO) > 0) {
            overallUtilPct = totalAllocated.multiply(BigDecimal.valueOf(100))
                    .divide(totalCapacity, 1, RoundingMode.HALF_UP);
        } else {
            overallUtilPct = BigDecimal.ZERO;
        }

        return new UtilizationSummaryDto(
                start,
                end,
                overallUtilPct,
                totalCapacity.setScale(1, RoundingMode.HALF_UP),
                totalAllocated.setScale(1, RoundingMode.HALF_UP),
                totalActual.setScale(1, RoundingMode.HALF_UP),
                overloadedMembersCount,
                members
        );
    }

    @Override
    public byte[] exportReport(String format, LocalDate startDate, LocalDate endDate) {
        UtilizationSummaryDto summary = getUtilizationSummary(startDate, endDate);
        if ("xlsx".equalsIgnoreCase(format)) {
            return ReportExporter.exportXlsx(summary);
        }
        return ReportExporter.exportCsv(summary);
    }

    @Override
    @Scheduled(cron = "0 0 8 * * MON", zone = "Asia/Jakarta")
    public void sendWeeklyUtilizationEmail() {
        LocalDate prevMonday = LocalDate.now().minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate prevSunday = prevMonday.plusDays(6);

        UtilizationSummaryDto summary = getUtilizationSummary(prevMonday, prevSunday);

        // Fetch recipients
        List<String> emails = jdbc.sql("""
            SELECT email FROM users WHERE role IN ('ADMIN', 'SUPERVISOR') AND active = TRUE AND deleted_at IS NULL
        """)
        .query((rs, rowNum) -> rs.getString("email"))
        .list();

        String recipientList = String.join(",", emails);
        log.info("Mengirim ringkasan mingguan pemanfaatan ({}) ke: {}", prevMonday, recipientList);

        if (mailSender != null && !emails.isEmpty()) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setTo(emails.toArray(new String[0]));
                msg.setSubject("[Toppa Load] Ringkasan Utilisasi & Kapasitas Mingguan (" + prevMonday + " - " + prevSunday + ")");
                msg.setText(String.format("""
                    Halo Tim Manajemen,

                    Berikut rekapitulasi beban kerja & utilisasi tim minggu lalu:
                    - Periode: %s s/d %s
                    - Rata-rata Utilisasi Tim: %s%%
                    - Total Jam Alokasi: %s jam
                    - Total Jam Aktual: %s jam
                    - Jumlah Anggota Overload: %d orang

                    Silakan akses Toppa Load di sistem untuk laporan lengkap dan proyeksi kapasitas mendatang.

                    Salam hangat,
                    Sistem Toppa Load
                    """,
                    prevMonday, prevSunday, summary.overallUtilizationPct(),
                    summary.totalAllocatedHours(), summary.totalActualHours(),
                    summary.overloadedMembersCount()
                ));
                mailSender.send(msg);
            } catch (Exception e) {
                log.warn("Gagal mengirim email rekapitulasi mingguan: {}", e.getMessage());
            }
        }

        // Record in scheduled_reports
        jdbc.sql("""
            INSERT INTO scheduled_reports (report_type, frequency, recipient_emails, last_run_at, next_run_at)
            VALUES ('WEEKLY_UTILIZATION', 'WEEKLY_MONDAY', :recipients, NOW(), NOW() + INTERVAL '7 days')
        """)
        .param("recipients", recipientList.isEmpty() ? "admin@toppaload.local" : recipientList)
        .update();
    }

    private record UserMeta(UUID userId, String email, BigDecimal weeklyCapacity) {}
}

