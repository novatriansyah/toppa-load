package com.toppaload.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SystemSettingsIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private UUID adminId;
    private UUID supervisorId;
    private UUID memberId;

    @BeforeEach
    void setup() {
        TestDatabaseCleaner.cleanAll(jdbc);

        // Reset system_settings to defaults
        jdbc.sql("""
            UPDATE system_settings
            SET underutilized_threshold_pct = 60.0,
                overloaded_threshold_pct = 100.0,
                work_timezone = 'Asia/Jakarta',
                updated_by = NULL,
                updated_at = NOW()
            WHERE id = 1
        """).update();

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

        supervisorId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
            VALUES (:id, 'supervisor@toppaload.local', 'Supervisor User', 'SUPERVISOR', true, 8.00, '{1,2,3,4,5}')
        """).param("id", supervisorId).update();

        memberId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
            VALUES (:id, 'member@toppaload.local', 'Member User', 'MEMBER', true, 8.00, '{1,2,3,4,5}')
        """).param("id", memberId).update();
    }

    private SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor auth(String email, String role) {
        return oidcLogin()
                .idToken(token -> token.claim("email", email).subject(email))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void getSettingsReturnsCurrentConfiguration() throws Exception {
        mockMvc.perform(get("/api/v1/settings")
                        .with(auth("member@toppaload.local", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.underutilizedThresholdPct").value(60.0))
                .andExpect(jsonPath("$.overloadedThresholdPct").value(100.0))
                .andExpect(jsonPath("$.workTimezone").value("Asia/Jakarta"));
    }

    @Test
    void adminCanUpdateSettingsAndChangesAffectHeatmapClassifier() throws Exception {
        // Create project and allocation: 8h/day on a 5-day week (40h capacity)
        // With 38.4 hours confirmed = exactly 96% utilization.
        // At default (60/100), 96% is OPTIMAL.
        // After updating overloaded threshold to 95%, 96% becomes OVERLOADED!
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, status, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-CFG', 'Config Project', 'ACTIVE', '2026-11-01', '2026-11-30', :ownerId)
        """).param("id", projectId).param("ownerId", adminId).update();

        // 4.8 hours/day over 4 days (Mon-Thu) = 19.2h, Friday 19.2h -> total 38.4h / 40h = 96%
        jdbc.sql("""
            INSERT INTO allocations (id, user_id, project_id, start_date, end_date, hours_per_day, allocation_type, created_by)
            VALUES (gen_random_uuid(), :userId, :projectId, '2026-11-02', '2026-11-06', 7.68, 'CONFIRMED', :adminId)
        """).param("userId", memberId).param("projectId", projectId).param("adminId", adminId).update();

        // Check before update: 96% is OPTIMAL
        mockMvc.perform(get("/api/v1/capacity/heatmap")
                        .param("from", "2026-11-02")
                        .param("to", "2026-11-08")
                        .param("granularity", "WEEK")
                        .with(auth("member@toppaload.local", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.userId=='" + memberId + "')].cells[0].status").value("OPTIMAL"));

        // Admin updates settings: underutilized=50.0, overloaded=95.0
        SystemSettingsDto newSettings = new SystemSettingsDto(
                new BigDecimal("50.0"),
                new BigDecimal("95.0"),
                "Asia/Jakarta"
        );

        mockMvc.perform(put("/api/v1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newSettings))
                        .with(csrf())
                        .with(auth("admin@toppaload.local", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.underutilizedThresholdPct").value(50.0))
                .andExpect(jsonPath("$.overloadedThresholdPct").value(95.0));

        // Audit log should be recorded
        Integer auditCount = jdbc.sql("""
            SELECT COUNT(*) FROM audit_logs
            WHERE entity_type = 'SYSTEM_SETTINGS' AND action = 'UPDATE'
        """).query(Integer.class).single();
        assertThat(auditCount).isGreaterThanOrEqualTo(1);

        // Heatmap now evaluates 96% as OVERLOADED (> 95%)!
        mockMvc.perform(get("/api/v1/capacity/heatmap")
                        .param("from", "2026-11-02")
                        .param("to", "2026-11-08")
                        .param("granularity", "WEEK")
                        .with(auth("member@toppaload.local", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.userId=='" + memberId + "')].cells[0].status").value("OVERLOADED"));
    }

    @Test
    void rejectsInvalidThresholdsWhereUnderutilizedIsGreaterThanOrEqualOverloaded() throws Exception {
        SystemSettingsDto invalidSettings = new SystemSettingsDto(
                new BigDecimal("100.0"),
                new BigDecimal("90.0"),
                "Asia/Jakarta"
        );

        mockMvc.perform(put("/api/v1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidSettings))
                        .with(csrf())
                        .with(auth("admin@toppaload.local", "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supervisorAndMemberCannotUpdateSettings() throws Exception {
        SystemSettingsDto settings = new SystemSettingsDto(
                new BigDecimal("50.0"),
                new BigDecimal("90.0"),
                "Asia/Jakarta"
        );

        mockMvc.perform(put("/api/v1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settings))
                        .with(csrf())
                        .with(auth("supervisor@toppaload.local", "SUPERVISOR")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settings))
                        .with(csrf())
                        .with(auth("member@toppaload.local", "MEMBER")))
                .andExpect(status().isForbidden());
    }
}
