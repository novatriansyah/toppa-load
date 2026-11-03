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
class SavedViewIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.cleanAll(jdbc);

        userId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, work_hours_per_day, work_days, active)
            VALUES (:id, 'user.view@toppaload.local', 'View Specialist', 'MEMBER', 8.0, '{1,2,3,4,5}', true)
        """).param("id", userId).update();
    }

    @Test
    @DisplayName("User dapat membuat, mengambil, dan menghapus saved views")
    void userCanCreateListAndDeleteSavedViews() throws Exception {
        String createJson = """
            {
              "viewType": "TASKS_KANBAN",
              "name": "Sprint 1 Kanban In Progress",
              "filterStateJson": {
                "status": "IN_PROGRESS",
                "priority": "HIGH"
              },
              "isShared": true
            }
        """;

        // 1. Create saved view
        mockMvc.perform(post("/api/v1/saved-views")
                        .with(oidcLogin().idToken(t -> t.claim("email", "user.view@toppaload.local")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Sprint 1 Kanban In Progress")))
                .andExpect(jsonPath("$.viewType", is("TASKS_KANBAN")))
                .andExpect(jsonPath("$.isShared", is(true)));

        // 2. List saved views
        mockMvc.perform(get("/api/v1/saved-views")
                        .param("viewType", "TASKS_KANBAN")
                        .with(oidcLogin().idToken(t -> t.claim("email", "user.view@toppaload.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Sprint 1 Kanban In Progress")));

        // 3. Delete saved view
        UUID savedId = jdbc.sql("SELECT id FROM saved_views WHERE user_id = :userId")
                .param("userId", userId)
                .query(rs -> {
                    rs.next();
                    return UUID.fromString(rs.getString("id"));
                });

        mockMvc.perform(delete("/api/v1/saved-views/{id}", savedId)
                        .with(oidcLogin().idToken(t -> t.claim("email", "user.view@toppaload.local")))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/v1/saved-views")
                        .param("viewType", "TASKS_KANBAN")
                        .with(oidcLogin().idToken(t -> t.claim("email", "user.view@toppaload.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}

