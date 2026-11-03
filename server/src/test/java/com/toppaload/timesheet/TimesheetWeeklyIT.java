package com.toppaload.timesheet;

import com.toppaload.TestcontainersConfiguration;
import com.toppaload.test.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimesheetWeeklyIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private UUID supervisorId;
    private UUID memberId;
    private UUID projectId;
    private UUID taskId;
    private final LocalDate monday = LocalDate.of(2026, 11, 2);

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.cleanAll(jdbc);

        supervisorId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, work_hours_per_day, work_days, active)
            VALUES (:id, 'spv@toppaload.local', 'Supervisor Rudi', 'SUPERVISOR', 8.0, '{1,2,3,4,5}', true)
        """).param("id", supervisorId).update();

        memberId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, work_hours_per_day, work_days, active)
            VALUES (:id, 'member.ani@toppaload.local', 'Ani Lestari', 'MEMBER', 8.0, '{1,2,3,4,5}', true)
        """).param("id", memberId).update();

        projectId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-OPS', 'Operasional Inti', '2026-11-01', '2026-12-31', :ownerId)
        """).param("id", projectId).param("ownerId", supervisorId).update();

        taskId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO tasks (id, project_id, title, estimated_hours, start_date, due_date, assignee_id)
            VALUES (:id, :projectId, 'Task Pengujian Beban', 20.0, '2026-11-02', '2026-11-06', :assigneeId)
        """)
        .param("id", taskId)
        .param("projectId", projectId)
        .param("assigneeId", memberId)
        .update();

        // 16 hours allocation (2 days x 8h)
        jdbc.sql("""
            INSERT INTO allocations (id, user_id, project_id, task_id, start_date, end_date, hours_per_day, created_by)
            VALUES (gen_random_uuid(), :userId, :projectId, :taskId, '2026-11-02', '2026-11-03', 8.0, :createdBy)
        """)
        .param("userId", memberId)
        .param("projectId", projectId)
        .param("taskId", taskId)
        .param("createdBy", supervisorId)
        .update();
    }

    @Test
    @DisplayName("Member dapat menyimpan draf timesheet dan ditolak jika jam > 24 per hari")
    void memberCanSaveDraftAndValidationRejectsOver24Hours() throws Exception {
        // Valid draft: 8h on Monday, 7h on Tuesday
        String validJson = """
            {
              "weekStartDate": "%s",
              "entries": [
                {
                  "projectId": "%s",
                  "taskId": "%s",
                  "entryDate": "2026-11-02",
                  "hours": 8.0,
                  "notes": "Mengerjakan modul A"
                },
                {
                  "projectId": "%s",
                  "taskId": "%s",
                  "entryDate": "2026-11-03",
                  "hours": 7.0,
                  "notes": "Lanjutan modul A"
                }
              ]
            }
        """.formatted(monday, projectId, taskId, projectId, taskId);

        mockMvc.perform(put("/api/v1/timesheets/weekly")
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.ani@toppaload.local")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.status", is("DRAFT")))
                .andExpect(jsonPath("$.entries", hasSize(2)));

        // Invalid: daily hours exceed 24h on Monday
        String invalidJson = """
            {
              "weekStartDate": "%s",
              "entries": [
                {
                  "projectId": "%s",
                  "taskId": "%s",
                  "entryDate": "2026-11-02",
                  "hours": 25.0,
                  "notes": "Tidak masuk akal"
                }
              ]
            }
        """.formatted(monday, projectId, taskId);

        mockMvc.perform(put("/api/v1/timesheets/weekly")
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.ani@toppaload.local")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Alur Lengkap: Submit oleh Member -> Approve oleh Supervisor")
    void memberCanSubmitAndSupervisorCanApprove() throws Exception {
        String draftJson = """
            {
              "weekStartDate": "%s",
              "entries": [
                {
                  "projectId": "%s",
                  "taskId": "%s",
                  "entryDate": "2026-11-02",
                  "hours": 8.0,
                  "notes": "Testing"
                }
              ]
            }
        """.formatted(monday, projectId, taskId);

        // 1. Save draft
        mockMvc.perform(put("/api/v1/timesheets/weekly")
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.ani@toppaload.local")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson))
                .andExpect(status().isOk());

        // 2. Submit
        String submitJson = """
            {
              "weekStartDate": "%s"
            }
        """.formatted(monday);

        mockMvc.perform(post("/api/v1/timesheets/weekly/submit")
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.ani@toppaload.local")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.status", is("SUBMITTED")));

        // 3. Member cannot edit while SUBMITTED
        mockMvc.perform(put("/api/v1/timesheets/weekly")
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.ani@toppaload.local")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson))
                .andExpect(status().isBadRequest());

        // 4. Supervisor sees in pending-approvals queue
        mockMvc.perform(get("/api/v1/timesheets/pending-approvals")
                        .with(oidcLogin().idToken(t -> t.claim("email", "spv@toppaload.local"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userFullName", is("Ani Lestari")));

        // 5. Supervisor approves
        UUID submissionId = jdbc.sql("SELECT id FROM timesheet_submissions WHERE user_id = :userId")
                .param("userId", memberId)
                .query(rs -> {
                    rs.next();
                    return UUID.fromString(rs.getString("id"));
                });

        mockMvc.perform(post("/api/v1/timesheets/{id}/approve", submissionId)
                        .with(oidcLogin().idToken(t -> t.claim("email", "spv@toppaload.local"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    @Test
    @DisplayName("Komparasi Estimasi vs Alokasi vs Aktual dihitung secara akurat")
    void timesheetComputesEstimatedVsAllocatedVsActualAccurately() throws Exception {
        // Save entries total 18h (Monday 10h, Tuesday 8h)
        String draftJson = """
            {
              "weekStartDate": "%s",
              "entries": [
                {
                  "projectId": "%s",
                  "taskId": "%s",
                  "entryDate": "2026-11-02",
                  "hours": 10.0,
                  "notes": "Testing Part 1"
                },
                {
                  "projectId": "%s",
                  "taskId": "%s",
                  "entryDate": "2026-11-03",
                  "hours": 8.0,
                  "notes": "Testing Part 2"
                }
              ]
            }
        """.formatted(monday, projectId, taskId, projectId, taskId);

        mockMvc.perform(put("/api/v1/timesheets/weekly")
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.ani@toppaload.local")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparisons", hasSize(1)))
                .andExpect(jsonPath("$.comparisons[0].taskTitle", is("Task Pengujian Beban")))
                .andExpect(jsonPath("$.comparisons[0].estimatedHours", closeTo(20.0, 0.01)))
                .andExpect(jsonPath("$.comparisons[0].allocatedHours", closeTo(16.0, 0.01)))
                .andExpect(jsonPath("$.comparisons[0].actualLoggedHours", closeTo(18.0, 0.01)));
    }

    @Test
    @DisplayName("Supervisor dapat melihat timesheet anggota lain, namun sesama member ditolak")
    void supervisorCanViewOtherMemberTimesheetAndMemberIsForbidden() throws Exception {
        // Supervisor views member timesheet -> 200 OK
        mockMvc.perform(get("/api/v1/timesheets/weekly")
                        .param("weekStartDate", monday.toString())
                        .param("userId", memberId.toString())
                        .with(oidcLogin().idToken(t -> t.claim("email", "spv@toppaload.local"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.userId", is(memberId.toString())));

        // Member attempts to view supervisor timesheet -> 403 Forbidden
        mockMvc.perform(get("/api/v1/timesheets/weekly")
                        .param("weekStartDate", monday.toString())
                        .param("userId", supervisorId.toString())
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.ani@toppaload.local"))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isForbidden());
    }
}

