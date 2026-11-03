package com.toppaload.project;

import com.toppaload.TestcontainersConfiguration;
import com.toppaload.test.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TaskCommentAndTeamsIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private UUID adminId;
    private UUID memberId;
    private UUID projectId;
    private UUID taskId;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.cleanAll(jdbc);

        adminId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, work_hours_per_day, work_days, active)
            VALUES (:id, 'admin.collab@toppaload.local', 'Admin Collab', 'ADMIN', 8.0, '{1,2,3,4,5}', true)
        """).param("id", adminId).update();

        memberId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, work_hours_per_day, work_days, active)
            VALUES (:id, 'member.collab@toppaload.local', 'Budi Collab', 'MEMBER', 8.0, '{1,2,3,4,5}', true)
        """).param("id", memberId).update();

        projectId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, description, status, start_date, end_date, owner_id)
            VALUES (:id, 'COL-01', 'Proyek Kolaborasi', 'Proyek uji coba komentar', 'ACTIVE', '2026-11-01', '2026-11-30', :ownerId)
        """).param("id", projectId).param("ownerId", adminId).update();

        taskId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO tasks (id, project_id, title, status, priority, progress_percent, estimated_hours, assignee_id)
            VALUES (:id, :projectId, 'Task Fitur Komentar', 'IN_PROGRESS', 'HIGH', 50, 16.0, :assigneeId)
        """).param("id", taskId).param("projectId", projectId).param("assigneeId", memberId).update();
    }

    @Test
    @DisplayName("Member dapat menambahkan komentar pada task dan melihat daftarnya")
    void memberCanAddAndListCommentsOnTask() throws Exception {
        String commentJson = """
            {
              "commentText": "Refactoring modul selesai, sedang menulis unit test."
            }
        """;

        // 1. Post comment
        mockMvc.perform(post("/api/v1/tasks/{taskId}/comments", taskId)
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.collab@toppaload.local")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId", is(taskId.toString())))
                .andExpect(jsonPath("$.commentText", is("Refactoring modul selesai, sedang menulis unit test.")))
                .andExpect(jsonPath("$.authorFullName", is("Budi Collab")));

        // 2. List comments
        mockMvc.perform(get("/api/v1/tasks/{taskId}/comments", taskId)
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.collab@toppaload.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].commentText", is("Refactoring modul selesai, sedang menulis unit test.")));
    }

    @Test
    @DisplayName("Admin dapat memperbarui pengaturan Teams Webhook URL dan menguji koneksinya")
    void adminCanConfigureTeamsWebhookAndTest() throws Exception {
        String settingsJson = """
            {
              "underutilizedThresholdPct": 65.0,
              "overloadedThresholdPct": 105.0,
              "workTimezone": "Asia/Jakarta",
              "teamsWebhookUrl": "https://company.webhook.office.com/webhookb2/dummy"
            }
        """;

        // Update settings
        mockMvc.perform(put("/api/v1/settings")
                        .with(oidcLogin().idToken(t -> t.claim("email", "admin.collab@toppaload.local"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamsWebhookUrl", is("https://company.webhook.office.com/webhookb2/dummy")));

        // Test webhook endpoint (returns 200 with result)
        mockMvc.perform(post("/api/v1/notifications/test-teams-webhook")
                        .with(oidcLogin().idToken(t -> t.claim("email", "admin.collab@toppaload.local"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookUrl\": \"\"}"))
                .andExpect(status().isOk());
    }
}
