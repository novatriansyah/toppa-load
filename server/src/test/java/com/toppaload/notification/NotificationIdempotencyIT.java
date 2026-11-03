package com.toppaload.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toppaload.TestcontainersConfiguration;
import com.toppaload.notification.internal.TeamsWebhookDispatcher;
import com.toppaload.notification.internal.WorkloadAlertScheduler;
import com.toppaload.test.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationIdempotencyIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    WorkloadAlertScheduler workloadAlertScheduler;

    @Autowired
    NotificationPublisher notificationPublisher;

    @Autowired
    TeamsWebhookDispatcher teamsWebhookDispatcher;

    @MockitoBean
    JavaMailSender javaMailSender;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private UUID supervisorId;
    private UUID workerId;
    private UUID noEmailWorkerId;
    private UUID projectId;

    @BeforeEach
    void setup() {
        TestDatabaseCleaner.cleanAll(jdbc);
        jdbc.sql("DELETE FROM notifications").update();

        supervisorId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days, email_notifications)
            VALUES (:id, 'supervisor@toppaload.local', 'Supervisor User', 'SUPERVISOR', true, 8.00, '{1,2,3,4,5}', true)
        """).param("id", supervisorId).update();

        workerId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days, email_notifications)
            VALUES (:id, 'worker@toppaload.local', 'Worker User', 'MEMBER', true, 8.00, '{1,2,3,4,5}', true)
        """).param("id", workerId).update();

        noEmailWorkerId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days, email_notifications)
            VALUES (:id, 'noemail@toppaload.local', 'No Email User', 'MEMBER', true, 8.00, '{1,2,3,4,5}', false)
        """).param("id", noEmailWorkerId).update();

        projectId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, status, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-NOTIF', 'Notif Project', 'ACTIVE', '2026-11-01', '2026-12-31', :ownerId)
        """).param("id", projectId).param("ownerId", supervisorId).update();
    }

    private SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor auth(String email, String role) {
        return oidcLogin()
                .idToken(token -> token.claim("email", email).subject(email))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void creatingAllocationDispatchesNotificationAndEmail() throws Exception {
        String payload = """
            {
                "userId": "%s",
                "projectId": "%s",
                "startDate": "2026-11-02",
                "endDate": "2026-11-06",
                "hoursPerDay": 8.00,
                "allocationType": "CONFIRMED",
                "notes": "Full time allocation"
            }
        """.formatted(workerId, projectId);

        mockMvc.perform(post("/api/v1/allocations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(auth("supervisor@toppaload.local", "SUPERVISOR")))
                .andExpect(status().isCreated());

        Integer count = jdbc.sql("SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND type = 'ALLOCATION_ASSIGNED'")
                .param("userId", workerId)
                .query(Integer.class)
                .single();
        assertThat(count).isEqualTo(1);

        verify(javaMailSender, atLeastOnce()).send(any(SimpleMailMessage.class));
    }

    @Test
    void schedulerRunningTwiceIsStrictlyIdempotentAntiDobel() {
        // Create task due in exactly 2 days from today WIB
        LocalDate todayWib = LocalDate.now();
        LocalDate dueInTwoDays = todayWib.plusDays(2);
        UUID taskId = UUID.randomUUID();

        jdbc.sql("""
            INSERT INTO tasks (id, project_id, title, status, priority, due_date, assignee_id)
            VALUES (:id, :projectId, 'Deadline Task', 'IN_PROGRESS', 'HIGH', :dueDate, :assigneeId)
        """).param("id", taskId)
                .param("projectId", projectId)
                .param("dueDate", dueInTwoDays)
                .param("assigneeId", workerId)
                .update();

        // Overload workerId for current week: 10h/day = 50h > 40h
        LocalDate monday = todayWib.minusDays(todayWib.getDayOfWeek().getValue() - 1);
        LocalDate friday = monday.plusDays(4);
        jdbc.sql("""
            INSERT INTO allocations (id, user_id, project_id, start_date, end_date, hours_per_day, allocation_type, created_by)
            VALUES (gen_random_uuid(), :userId, :projectId, :start, :end, 10.00, 'CONFIRMED', :supervisorId)
        """).param("userId", workerId)
                .param("projectId", projectId)
                .param("start", monday)
                .param("end", friday)
                .param("supervisorId", supervisorId)
                .update();

        // First run of daily checks
        workloadAlertScheduler.runDailyChecks();

        Integer firstDueCount = jdbc.sql("SELECT COUNT(*) FROM notifications WHERE type = 'TASK_DUE_SOON'")
                .query(Integer.class).single();
        Integer firstOverloadCount = jdbc.sql("SELECT COUNT(*) FROM notifications WHERE type = 'OVERLOAD_WARNING'")
                .query(Integer.class).single();

        assertThat(firstDueCount).isEqualTo(1);
        assertThat(firstOverloadCount).isEqualTo(1);

        // Run second time immediately on the same day
        workloadAlertScheduler.runDailyChecks();

        Integer secondDueCount = jdbc.sql("SELECT COUNT(*) FROM notifications WHERE type = 'TASK_DUE_SOON'")
                .query(Integer.class).single();
        Integer secondOverloadCount = jdbc.sql("SELECT COUNT(*) FROM notifications WHERE type = 'OVERLOAD_WARNING'")
                .query(Integer.class).single();

        // Exactly 1 each! NO DUPLICATION!
        assertThat(secondDueCount).isEqualTo(1);
        assertThat(secondOverloadCount).isEqualTo(1);
    }

    @Test
    void userWithEmailNotificationsDisabledSkipsSmtp() {
        boolean published = notificationPublisher.publishIfAbsent(
                noEmailWorkerId,
                NotificationType.OVERLOAD_WARNING,
                "Beban Kerja Berlebih",
                Map.of("hours", 45),
                "OVERLOAD:NO_EMAIL_TEST:1"
        );
        assertThat(published).isTrue();

        String emailStatus = jdbc.sql("SELECT email_status FROM notifications WHERE user_id = :userId")
                .param("userId", noEmailWorkerId)
                .query(String.class)
                .single();

        assertThat(emailStatus).isEqualTo("SKIPPED");
    }

    @Test
    void inAppNotificationLifecycleMarkAsReadAndMarkAll() throws Exception {
        // Insert 2 unread notifications for workerId
        notificationPublisher.publishIfAbsent(workerId, NotificationType.TASK_DUE_SOON, "Task 1 Due", Map.of(), "DUE:1");
        notificationPublisher.publishIfAbsent(workerId, NotificationType.TASK_DUE_SOON, "Task 2 Due", Map.of(), "DUE:2");

        // List notifications
        mockMvc.perform(get("/api/v1/notifications")
                        .with(auth("worker@toppaload.local", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));

        UUID firstNotifId = jdbc.sql("SELECT id FROM notifications WHERE dedup_key = 'DUE:1'")
                .query(UUID.class).single();

        // Mark 1 as read
        mockMvc.perform(post("/api/v1/notifications/" + firstNotifId + "/read")
                        .with(csrf())
                        .with(auth("worker@toppaload.local", "MEMBER")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/notifications")
                        .with(auth("worker@toppaload.local", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        // Mark all as read
        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .with(csrf())
                        .with(auth("worker@toppaload.local", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedCount").value(1));

        mockMvc.perform(get("/api/v1/notifications")
                        .with(auth("worker@toppaload.local", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }
}

