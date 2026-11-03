package com.toppaload.allocation;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AllocationCsvImportIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    private UUID adminId;
    private UUID supervisorId;
    private UUID worker1Id;
    private UUID worker2Id;

    @BeforeEach
    void setup() {
        TestDatabaseCleaner.cleanAll(jdbc);

        adminId = jdbc.sql("SELECT id FROM users WHERE email = 'admin@toppaload.local'").query(UUID.class).optional()
                .orElseGet(() -> {
                    UUID id = UUID.randomUUID();
                    jdbc.sql("""
                        INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
                        VALUES (:id, 'admin@toppaload.local', 'Admin User', 'ADMIN', true, 8.00, '{1,2,3,4,5}')
                    """).param("id", id).update();
                    return id;
                });

        supervisorId = UUID.randomUUID();
        worker1Id = UUID.randomUUID();
        worker2Id = UUID.randomUUID();

        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
            VALUES (:id, 'spv@toppaload.local', 'Supervisor User', 'SUPERVISOR', true, 8.00, '{1,2,3,4,5}')
        """).param("id", supervisorId).update();

        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
            VALUES (:id, 'budi@toppaload.local', 'Budi Santoso', 'MEMBER', true, 8.00, '{1,2,3,4,5}')
        """).param("id", worker1Id).update();

        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
            VALUES (:id, 'siti@toppaload.local', 'Siti Rahma', 'MEMBER', true, 8.00, '{1,2,3,4,5}')
        """).param("id", worker2Id).update();
    }

    private SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor auth(String email, String role) {
        return oidcLogin()
                .idToken(token -> token.claim("email", email).subject(email))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void supervisorCanImportValidOnboardingCsv() throws Exception {
        String csv = """
            email,project_code,project_name,task_title,start_date,end_date,hours_per_day,allocation_type,notes
            budi@toppaload.local,PRJ-ALPHA,Project Alpha,Core Backend,2026-11-02,2026-11-06,8.00,CONFIRMED,Sprint 1
            siti@toppaload.local,PRJ-ALPHA,Project Alpha,Frontend UI,2026-11-02,2026-11-06,6.00,CONFIRMED,Dashboard
            siti@toppaload.local,PRJ-BETA,Project Beta,Mobile App,2026-11-09,2026-11-13,8.00,TENTATIVE,Phase 2
        """;

        mockMvc.perform(post("/api/v1/allocations/import-csv")
                        .with(csrf())
                        .contentType("text/csv")
                        .content(csv)
                        .with(auth("spv@toppaload.local", "SUPERVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(3))
                .andExpect(jsonPath("$.projectsCreated").value(2))
                .andExpect(jsonPath("$.tasksCreated").value(3));

        Integer allocCount = jdbc.sql("SELECT COUNT(*) FROM allocations WHERE deleted_at IS NULL").query(Integer.class).single();
        assertThat(allocCount).isEqualTo(3);

        Integer projCount = jdbc.sql("SELECT COUNT(*) FROM projects WHERE code IN ('PRJ-ALPHA', 'PRJ-BETA')").query(Integer.class).single();
        assertThat(projCount).isEqualTo(2);
    }

    @Test
    void rejectsEntireBatchIfAnyEmailIsUnknownAllOrNothing() throws Exception {
        String csv = """
            email,project_code,project_name,task_title,start_date,end_date,hours_per_day,allocation_type,notes
            budi@toppaload.local,PRJ-GAMMA,Project Gamma,Task 1,2026-11-02,2026-11-06,8.00,CONFIRMED,Good row
            unknown.user@external.com,PRJ-GAMMA,Project Gamma,Task 2,2026-11-02,2026-11-06,8.00,CONFIRMED,Bad row
        """;

        mockMvc.perform(post("/api/v1/allocations/import-csv")
                        .with(csrf())
                        .contentType("text/csv")
                        .content(csv)
                        .with(auth("spv@toppaload.local", "SUPERVISOR")))
                .andExpect(status().isBadRequest());

        // All-or-nothing: PRJ-GAMMA should NOT exist in database!
        Integer projCount = jdbc.sql("SELECT COUNT(*) FROM projects WHERE code = 'PRJ-GAMMA'").query(Integer.class).single();
        assertThat(projCount).isEqualTo(0);

        Integer allocCount = jdbc.sql("SELECT COUNT(*) FROM allocations").query(Integer.class).single();
        assertThat(allocCount).isEqualTo(0);
    }

    @Test
    void memberCannotImportCsv() throws Exception {
        String csv = "email,project_code,project_name,task_title,start_date,end_date,hours_per_day,allocation_type,notes\n";

        mockMvc.perform(post("/api/v1/allocations/import-csv")
                        .with(csrf())
                        .contentType("text/csv")
                        .content(csv)
                        .with(auth("budi@toppaload.local", "MEMBER")))
                .andExpect(status().isForbidden());
    }
}

