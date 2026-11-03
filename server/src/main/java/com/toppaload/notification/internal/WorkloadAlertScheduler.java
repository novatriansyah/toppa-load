package com.toppaload.notification.internal;

import com.toppaload.capacity.CapacityQueryService;
import com.toppaload.capacity.Granularity;
import com.toppaload.capacity.HeatmapResponse;
import com.toppaload.capacity.UtilizationStatus;
import com.toppaload.notification.NotificationPublisher;
import com.toppaload.notification.NotificationType;
import com.toppaload.notification.TeamsNotificationGateway;
import com.toppaload.shared.WibClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class WorkloadAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkloadAlertScheduler.class);

    private final CapacityQueryService capacityQueryService;
    private final NotificationPublisher notificationPublisher;
    private final TeamsNotificationGateway teamsNotificationGateway;
    private final WibClock wibClock;
    private final JdbcClient jdbc;

    public WorkloadAlertScheduler(
            CapacityQueryService capacityQueryService,
            NotificationPublisher notificationPublisher,
            TeamsNotificationGateway teamsNotificationGateway,
            WibClock wibClock,
            JdbcClient jdbc
    ) {
        this.capacityQueryService = capacityQueryService;
        this.notificationPublisher = notificationPublisher;
        this.teamsNotificationGateway = teamsNotificationGateway;
        this.wibClock = wibClock;
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Jakarta")
    public void runDailyChecks() {
        log.info("Running daily notification checks (H-2 due tasks and weekly overload alerts)...");
        scanDueSoonTasks();
        scanOverloadedUsers();
    }

    @Scheduled(cron = "0 0 16 * * FRI", zone = "Asia/Jakarta")
    public void sendFridayTimesheetReminders() {
        log.info("Sending Friday afternoon timesheet reminders to Microsoft Teams...");
        LocalDate today = wibClock.todayWib();
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1);

        teamsNotificationGateway.sendAdaptiveCard(
                "Pengingat Timesheet Mingguan (Jumat Sore)",
                "Selamat sore rekan-rekan! Harap luangkan waktu untuk melengkapi dan melakukan Submit pada timesheet kerja mingguan Anda sebelum akhir jam kerja.",
                Map.of(
                        "Periode Minggu", monday.toString(),
                        "Batas Submit", today + " 17:00 WIB",
                        "Menu", "Timesheet Mingguan (/timesheet)"
                )
        );
    }

    public void scanDueSoonTasks() {
        LocalDate today = wibClock.todayWib();
        LocalDate targetDate = today.plusDays(2);

        record DueSoonTask(UUID id, String title, UUID assigneeId, LocalDate dueDate, String projectName) {}

        String sql = """
            SELECT t.id, t.title, t.assignee_id, t.due_date, p.name AS project_name
            FROM tasks t
            JOIN projects p ON p.id = t.project_id
            WHERE t.deleted_at IS NULL
              AND t.status NOT IN ('DONE', 'CANCELLED')
              AND t.assignee_id IS NOT NULL
              AND t.due_date = :targetDate
        """;

        List<DueSoonTask> tasks = jdbc.sql(sql)
                .param("targetDate", targetDate)
                .query((rs, rowNum) -> new DueSoonTask(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("assignee_id", UUID.class),
                        rs.getDate("due_date").toLocalDate(),
                        rs.getString("project_name")
                ))
                .list();

        for (var task : tasks) {
            String dedupKey = "DUE_SOON:" + task.assigneeId() + ":" + task.id() + ":" + targetDate;
            String title = "Pengingat Batas Waktu H-2: " + task.title();
            Map<String, Object> payload = Map.of(
                    "taskId", task.id().toString(),
                    "title", task.title(),
                    "projectName", task.projectName(),
                    "dueDate", targetDate.toString()
            );

            notificationPublisher.publishIfAbsent(
                    task.assigneeId(),
                    NotificationType.TASK_DUE_SOON,
                    title,
                    payload,
                    dedupKey
            );
        }
    }

    public void scanOverloadedUsers() {
        LocalDate today = wibClock.todayWib();
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate sunday = monday.plusDays(6);

        HeatmapResponse heatmap = capacityQueryService.getHeatmap(monday, sunday, Granularity.WEEK, false);

        for (var row : heatmap.rows()) {
            for (var cell : row.cells()) {
                if (cell.status() == UtilizationStatus.OVERLOADED) {
                    String dedupKey = "OVERLOAD:" + row.userId() + ":" + monday;
                    String title = "Peringatan Beban Kerja Berlebih: " + row.fullName() + " (" + cell.utilizationPct() + "%)";
                    Map<String, Object> payload = Map.of(
                            "weekStart", monday.toString(),
                            "weekEnd", sunday.toString(),
                            "utilizationPct", cell.utilizationPct() != null ? cell.utilizationPct() : 0,
                            "confirmedHours", cell.confirmedHours(),
                            "netCapacityHours", cell.netCapacityHours()
                    );

                    boolean published = notificationPublisher.publishIfAbsent(
                            row.userId(),
                            NotificationType.OVERLOAD_WARNING,
                            title,
                            payload,
                            dedupKey
                    );

                    if (published) {
                        teamsNotificationGateway.sendAdaptiveCard(
                                "Peringatan Beban Lebih: " + row.fullName(),
                                "Anggota tim memiliki beban kerja melebihi kapasitas (" + cell.utilizationPct() + "%). Harap seimbangkan kembali alokasi proyek.",
                                Map.of(
                                        "Anggota Tim", row.fullName(),
                                        "Utilisasi", cell.utilizationPct() + "%",
                                        "Jam Alokasi", cell.confirmedHours() + " jam",
                                        "Kapasitas Bersih", cell.netCapacityHours() + " jam",
                                        "Minggu", monday + " s/d " + sunday
                                )
                        );
                    }
                }
            }
        }
    }
}
