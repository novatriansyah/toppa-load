package com.toppaload.audit;

import com.toppaload.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditRecorderIT {

    @Autowired
    AuditRecorder auditRecorder;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void cleanUp() {
        jdbc.sql("DELETE FROM audit_logs").update();
        jdbc.sql("DELETE FROM users").update();
    }

    @Test
    void persistsBeforeAndAfterSnapshotsAsQueryableJsonb() {
        auditRecorder.record(
                null,
                "supervisor@kantor.id",
                "UPDATE",
                "PROJECT",
                "proj-1",
                Map.of("status", "PLANNING"),
                Map.of("status", "ACTIVE")
        );

        String status = jdbc.sql("SELECT after_data->>'status' FROM audit_logs WHERE entity_id = 'proj-1'")
                .query(String.class)
                .single();

        assertThat(status).isEqualTo("ACTIVE");

        String beforeStatus = jdbc.sql("SELECT before_data->>'status' FROM audit_logs WHERE entity_id = 'proj-1'")
                .query(String.class)
                .single();

        assertThat(beforeStatus).isEqualTo("PLANNING");
    }

    @Test
    void persistsNullSnapshotsForCreationAndDeletion() {
        auditRecorder.record(
                null,
                "admin@kantor.id",
                "CREATE",
                "USER",
                "user-1",
                null,
                Map.of("email", "new@kantor.id")
        );

        String email = jdbc.sql("SELECT after_data->>'email' FROM audit_logs WHERE entity_id = 'user-1'")
                .query(String.class)
                .single();
        assertThat(email).isEqualTo("new@kantor.id");

        Boolean beforeIsNull = jdbc.sql("SELECT (before_data IS NULL) FROM audit_logs WHERE entity_id = 'user-1'")
                .query(Boolean.class)
                .single();
        assertThat(beforeIsNull).isTrue();
    }

    @Test
    @WithMockUser(username = "supervisor@kantor.id", roles = "SUPERVISOR")
    void controllerListsAuditEntries() throws Exception {
        UUID actorId = UUID.randomUUID();
        jdbc.sql("INSERT INTO users (id, email, full_name, role) VALUES (:id, 'supervisor@kantor.id', 'Supervisor', 'SUPERVISOR')")
                .param("id", actorId)
                .update();

        auditRecorder.record(
                actorId,
                "supervisor@kantor.id",
                "UPDATE",
                "PROJECT",
                "proj-1",
                Map.of("status", "PLANNING"),
                Map.of("status", "ACTIVE")
        );
        auditRecorder.record(
                actorId,
                "admin@kantor.id",
                "DELETE",
                "USER",
                "usr-99",
                Map.of("role", "MEMBER"),
                null
        );

        mockMvc.perform(get("/api/v1/audit-logs").param("entityType", "PROJECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].entityType").value("PROJECT"))
                .andExpect(jsonPath("$[0].entityId").value("proj-1"))
                .andExpect(jsonPath("$[0].actorEmail").value("supervisor@kantor.id"))
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].actorId").value(actorId.toString()))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "supervisor@kantor.id", roles = "SUPERVISOR")
    void controllerFiltersByEntityTypeAndEntityId() throws Exception {
        jdbc.sql("INSERT INTO users (email, full_name, role) VALUES ('supervisor@kantor.id', 'Supervisor', 'SUPERVISOR')")
                .update();

        auditRecorder.record(null, "u1@kantor.id", "CREATE", "TASK", "task-1", null, Map.of("title", "T1"));
        auditRecorder.record(null, "u1@kantor.id", "UPDATE", "TASK", "task-2", null, Map.of("title", "T2"));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "TASK")
                        .param("entityId", "task-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].entityId").value("task-1"));
    }
}
