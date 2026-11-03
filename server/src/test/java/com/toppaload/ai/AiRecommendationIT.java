package com.toppaload.ai;

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

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AiRecommendationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private UUID adminId;
    private UUID userJava;
    private UUID userReact;
    private UUID taskId;

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

        userJava = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
            VALUES (:id, 'java.dev@toppaload.local', 'Budi Java Engineer', 'MEMBER', true, 8.00, '{1,2,3,4,5}')
        """).param("id", userJava).update();

        userReact = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
            VALUES (:id, 'react.dev@toppaload.local', 'Siti Frontend Specialist', 'MEMBER', true, 8.00, '{1,2,3,4,5}')
        """).param("id", userReact).update();

        // Add skills
        jdbc.sql("INSERT INTO user_skills (user_id, skill_name, proficiency_level) VALUES (:userId, 'Spring Boot', 'EXPERT')")
                .param("userId", userJava).update();
        jdbc.sql("INSERT INTO user_skills (user_id, skill_name, proficiency_level) VALUES (:userId, 'React', 'EXPERT')")
                .param("userId", userReact).update();

        // Project and task
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, description, status, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-AI', 'AI Test Project', 'Desc', 'ACTIVE', '2026-11-01', '2026-11-30', :ownerId)
        """)
        .param("id", projectId)
        .param("ownerId", adminId)
        .update();

        taskId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO tasks (id, project_id, title, description, status, estimated_hours, start_date, due_date)
            VALUES (:id, :projId, 'Implement Spring Boot backend service', 'Build secure REST endpoints', 'TODO', 16.0, '2026-11-05', '2026-11-12')
        """)
        .param("id", taskId)
        .param("projId", projectId)
        .update();

        // Ensure AI is enabled
        jdbc.sql("UPDATE system_settings SET ai_recommendations_enabled = TRUE WHERE id = 1").update();
    }

    private SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor auth(String email, String role) {
        return oidcLogin()
                .idToken(token -> token.claim("email", email).subject(email))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void recommendAssigneePrioritizesSkillAndCapacity() throws Exception {
        mockMvc.perform(get("/api/v1/ai/recommend-assignee")
                        .param("taskId", taskId.toString())
                        .with(auth("admin@toppaload.local", "ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(true))
                .andExpect(jsonPath("$.recommendations").isArray())
                // Top candidate should be Budi Java Engineer due to Spring Boot skill match!
                .andExpect(jsonPath("$.recommendations[0].fullName").value("Budi Java Engineer"))
                .andExpect(jsonPath("$.recommendations[0].matchingSkills[0]").value("Spring Boot (EXPERT)"))
                .andExpect(jsonPath("$.recommendations[0].matchScore", greaterThanOrEqualTo(60)));
    }

    @Test
    void whenAiDisabledInSettings_returnsDisabledResponse() throws Exception {
        jdbc.sql("UPDATE system_settings SET ai_recommendations_enabled = FALSE WHERE id = 1").update();

        mockMvc.perform(get("/api/v1/ai/recommend-assignee")
                        .param("taskId", taskId.toString())
                        .with(auth("admin@toppaload.local", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(false))
                .andExpect(jsonPath("$.recommendations").isEmpty());
    }

    @Test
    void getTeamBalanceSuggestionsReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/ai/balance-suggestions")
                        .with(auth("admin@toppaload.local", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void applyBalanceSuggestionRebalancesAllocationAndTask() throws Exception {
        UUID allocId = UUID.randomUUID();
        UUID projId = jdbc.sql("SELECT project_id FROM tasks WHERE id = :id").param("id", taskId).query(UUID.class).single();

        jdbc.sql("""
            INSERT INTO allocations (id, user_id, project_id, task_id, start_date, end_date, hours_per_day, allocation_type, created_by)
            VALUES (:id, :userId, :projId, :taskId, CURRENT_DATE, CURRENT_DATE + 5, 4.0, 'CONFIRMED', :adminId)
        """)
        .param("id", allocId)
        .param("userId", userJava)
        .param("projId", projId)
        .param("taskId", taskId)
        .param("adminId", adminId)
        .update();

        // Ensure task is initially assigned to userJava
        jdbc.sql("UPDATE tasks SET assignee_id = :userId WHERE id = :id")
                .param("userId", userJava)
                .param("id", taskId)
                .update();

        String body = """
            {
                "allocationId": "%s",
                "candidateUserId": "%s",
                "taskId": "%s"
            }
        """.formatted(allocId, userReact, taskId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/ai/balance-suggestions/apply")
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(auth("admin@toppaload.local", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.newUserId").value(userReact.toString()));

        // Verify allocation was updated in database
        UUID updatedAllocUser = jdbc.sql("SELECT user_id FROM allocations WHERE id = :id")
                .param("id", allocId)
                .query(UUID.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(updatedAllocUser).isEqualTo(userReact);

        // Verify task assignee was updated in database
        UUID updatedTaskAssignee = jdbc.sql("SELECT assignee_id FROM tasks WHERE id = :id")
                .param("id", taskId)
                .query(UUID.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(updatedTaskAssignee).isEqualTo(userReact);
    }
}

