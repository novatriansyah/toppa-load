package com.toppaload.reporting;

import com.toppaload.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportingIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private UUID userId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.cleanAll(jdbc);

        var existingAdmin = jdbc.sql("SELECT id FROM users WHERE email = 'admin@toppaload.local'").query(UUID.class).optional();
        if (existingAdmin.isPresent()) {
            adminId = existingAdmin.get();
        } else {
            adminId = UUID.randomUUID();
            jdbc.sql("""
                INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
                VALUES (:id, 'admin@toppaload.local', 'Admin User', 'ADMIN', true, 8.00, '{1,2,3,4,5}')
            """).param("id", adminId).update();
        }

        userId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
            VALUES (:id, 'analyst@toppaload.local', 'Data Analyst', 'MEMBER', TRUE, 8.0, '{1,2,3,4,5}')
        """)
        .param("id", userId)
        .update();

        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, description, status, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-ANALYTICS', 'Analytics Portal', 'Analytics Portal Desc', 'ACTIVE', '2026-11-01', '2026-11-30', :ownerId)
        """)
        .param("id", projectId)
        .param("ownerId", adminId)
        .update();

        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate friday = monday.plusDays(4);

        // Add 30 hours allocation
        jdbc.sql("""
            INSERT INTO allocations (id, project_id, user_id, start_date, end_date, hours_per_day, allocation_type, created_by)
            VALUES (gen_random_uuid(), :projId, :userId, :start, :end, 6.0, 'CONFIRMED', :adminId)
        """)
        .param("projId", projectId)
        .param("userId", userId)
        .param("start", monday)
        .param("end", friday)
        .param("adminId", adminId)
        .update();

        // Add 8 hours timesheet entry
        jdbc.sql("""
            INSERT INTO time_entries (id, user_id, project_id, entry_date, hours, notes)
            VALUES (gen_random_uuid(), :userId, :projId, :date, 8.0, 'Work session')
        """)
        .param("userId", userId)
        .param("projId", projectId)
        .param("date", monday)
        .update();
    }

    private SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor auth(String email, String role) {
        return oidcLogin()
                .idToken(token -> token.claim("email", email).subject(email))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void getUtilizationSummaryReturnsCalculatedData() throws Exception {
        mockMvc.perform(get("/api/v1/reports/utilization")
                        .with(auth("admin@toppaload.local", "ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallUtilizationPct").isNumber())
                .andExpect(jsonPath("$.members").isArray());
    }

    @Test
    void exportCsvReturnsValidCsvFormat() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reports/export")
                        .param("format", "csv")
                        .with(auth("admin@toppaload.local", "ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        byte[] csvBytes = result.getResponse().getContentAsByteArray();
        assertThat(csvBytes).isNotNull();
        String csvContent = new String(csvBytes);
        assertThat(csvContent).contains("Laporan Utilisasi Tim Toppa Load");
        assertThat(csvContent).contains("Data Analyst");
        assertThat(csvContent).contains("analyst@toppaload.local");
    }

    @Test
    void exportXlsxReturnsValidExcelFile() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reports/export")
                        .param("format", "xlsx")
                        .with(auth("admin@toppaload.local", "ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        byte[] xlsxBytes = result.getResponse().getContentAsByteArray();
        assertThat(xlsxBytes).isNotNull();
        // Check zip/xlsx magic bytes "PK" (0x50, 0x4B)
        assertThat(xlsxBytes[0]).isEqualTo((byte) 0x50);
        assertThat(xlsxBytes[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    void triggerWeeklySummaryRecordsScheduledReport() throws Exception {
        mockMvc.perform(post("/api/v1/reports/send-summary")
                        .with(csrf())
                        .with(auth("admin@toppaload.local", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());

        int count = jdbc.sql("SELECT COUNT(*) FROM scheduled_reports WHERE report_type = 'WEEKLY_UTILIZATION'")
                .query(Integer.class)
                .single();
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
