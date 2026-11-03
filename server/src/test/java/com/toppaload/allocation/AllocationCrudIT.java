package com.toppaload.allocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toppaload.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AllocationCrudIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private UUID supervisorId;
    private UUID memberId;
    private UUID otherMemberId;
    private UUID projectId1;
    private UUID projectId2;
    private UUID taskId1;

    @BeforeEach
    void setup() {
        com.toppaload.test.TestDatabaseCleaner.cleanAll(jdbc);

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

        otherMemberId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
            VALUES (:id, 'other@toppaload.local', 'Other User', 'MEMBER', true, 8.00, '{1,2,3,4,5}')
        """).param("id", otherMemberId).update();

        projectId1 = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, status, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-ALPHA', 'Project Alpha', 'ACTIVE', '2026-01-01', '2026-12-31', :ownerId)
        """).param("id", projectId1).param("ownerId", supervisorId).update();

        projectId2 = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, status, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-BETA', 'Project Beta', 'ACTIVE', '2026-01-01', '2026-12-31', :ownerId)
        """).param("id", projectId2).param("ownerId", supervisorId).update();

        taskId1 = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO tasks (id, project_id, title, status, priority, estimated_hours, start_date, due_date, progress_percent, assignee_id)
            VALUES (:id, :projectId, 'Task 1 in Project Alpha', 'IN_PROGRESS', 'HIGH', 16.00, '2026-11-01', '2026-11-15', 20, :assigneeId)
        """).param("id", taskId1).param("projectId", projectId1).param("assigneeId", memberId).update();
    }

    @Test
    @WithMockUser(username = "supervisor@toppaload.local", roles = "SUPERVISOR")
    void supervisorCanCreateUpdateAndDeleteAllocation() throws Exception {
        String payload = """
            {
                "userId": "%s",
                "projectId": "%s",
                "taskId": "%s",
                "startDate": "2026-11-02",
                "endDate": "2026-11-06",
                "hoursPerDay": 6.50,
                "allocationType": "CONFIRMED",
                "notes": "Backend API development"
            }
        """.formatted(memberId, projectId1, taskId1);

        MvcResult createResult = mockMvc.perform(post("/api/v1/allocations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.userId").value(memberId.toString()))
            .andExpect(jsonPath("$.projectId").value(projectId1.toString()))
            .andExpect(jsonPath("$.projectCode").value("PRJ-ALPHA"))
            .andExpect(jsonPath("$.taskId").value(taskId1.toString()))
            .andExpect(jsonPath("$.hoursPerDay").value(6.5))
            .andExpect(jsonPath("$.allocationType").value("CONFIRMED"))
            .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID allocId = UUID.fromString(created.get("id").asText());

        // List allocations
        mockMvc.perform(get("/api/v1/allocations")
                .param("userId", memberId.toString())
                .param("from", "2026-11-01")
                .param("to", "2026-11-07"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(allocId.toString()));

        // Update allocation
        String updatePayload = """
            {
                "userId": "%s",
                "projectId": "%s",
                "taskId": "%s",
                "startDate": "2026-11-02",
                "endDate": "2026-11-06",
                "hoursPerDay": 8.00,
                "allocationType": "CONFIRMED",
                "notes": "Full time on API development"
            }
        """.formatted(memberId, projectId1, taskId1);

        mockMvc.perform(put("/api/v1/allocations/" + allocId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hoursPerDay").value(8.0))
            .andExpect(jsonPath("$.notes").value("Full time on API development"));

        // Delete allocation
        mockMvc.perform(delete("/api/v1/allocations/" + allocId)
                .with(csrf()))
            .andExpect(status().isNoContent());

        // Verify soft-deleted from list
        mockMvc.perform(get("/api/v1/allocations")
                .param("userId", memberId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        // Verify audit logs recorded CREATE, UPDATE, DELETE
        Long auditCount = jdbc.sql("SELECT COUNT(*) FROM audit_logs WHERE UPPER(entity_type) = 'ALLOCATION' AND entity_id = :id")
            .param("id", allocId.toString())
            .query(Long.class)
            .single();
        assertThat(auditCount).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = "member@toppaload.local", roles = "MEMBER")
    void memberCanCreateTentativeAllocationForSelf() throws Exception {
        String payload = """
            {
                "userId": "%s",
                "projectId": "%s",
                "startDate": "2026-11-09",
                "endDate": "2026-11-13",
                "hoursPerDay": 4.00,
                "allocationType": "TENTATIVE",
                "notes": "Proposed support work"
            }
        """.formatted(memberId, projectId1);

        mockMvc.perform(post("/api/v1/allocations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.allocationType").value("TENTATIVE"))
            .andExpect(jsonPath("$.userId").value(memberId.toString()));
    }

    @Test
    @WithMockUser(username = "member@toppaload.local", roles = "MEMBER")
    void memberCannotCreateConfirmedAllocation() throws Exception {
        String payload = """
            {
                "userId": "%s",
                "projectId": "%s",
                "startDate": "2026-11-09",
                "endDate": "2026-11-13",
                "hoursPerDay": 4.00,
                "allocationType": "CONFIRMED",
                "notes": "Illegal confirmed allocation"
            }
        """.formatted(memberId, projectId1);

        mockMvc.perform(post("/api/v1/allocations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@toppaload.local", roles = "MEMBER")
    void memberCannotCreateAllocationForOthers() throws Exception {
        String payload = """
            {
                "userId": "%s",
                "projectId": "%s",
                "startDate": "2026-11-09",
                "endDate": "2026-11-13",
                "hoursPerDay": 4.00,
                "allocationType": "TENTATIVE",
                "notes": "Trying to allocate for peer"
            }
        """.formatted(otherMemberId, projectId1);

        mockMvc.perform(post("/api/v1/allocations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "supervisor@toppaload.local", roles = "SUPERVISOR")
    void mismatchedTaskAndProjectReturns400() throws Exception {
        // taskId1 belongs to projectId1, but request specifies projectId2
        String payload = """
            {
                "userId": "%s",
                "projectId": "%s",
                "taskId": "%s",
                "startDate": "2026-11-02",
                "endDate": "2026-11-06",
                "hoursPerDay": 6.00,
                "allocationType": "CONFIRMED"
            }
        """.formatted(memberId, projectId2, taskId1);

        mockMvc.perform(post("/api/v1/allocations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "supervisor@toppaload.local", roles = "SUPERVISOR")
    void invalidDatesOrHoursReturns400() throws Exception {
        // endDate < startDate
        String invalidDates = """
            {
                "userId": "%s",
                "projectId": "%s",
                "startDate": "2026-11-10",
                "endDate": "2026-11-05",
                "hoursPerDay": 6.00,
                "allocationType": "CONFIRMED"
            }
        """.formatted(memberId, projectId1);

        mockMvc.perform(post("/api/v1/allocations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidDates))
            .andExpect(status().isBadRequest());

        // hoursPerDay <= 0
        String zeroHours = """
            {
                "userId": "%s",
                "projectId": "%s",
                "startDate": "2026-11-02",
                "endDate": "2026-11-06",
                "hoursPerDay": 0.00,
                "allocationType": "CONFIRMED"
            }
        """.formatted(memberId, projectId1);

        mockMvc.perform(post("/api/v1/allocations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(zeroHours))
            .andExpect(status().isBadRequest());

        // hoursPerDay > 24
        String tooManyHours = """
            {
                "userId": "%s",
                "projectId": "%s",
                "startDate": "2026-11-02",
                "endDate": "2026-11-06",
                "hoursPerDay": 25.00,
                "allocationType": "CONFIRMED"
            }
        """.formatted(memberId, projectId1);

        mockMvc.perform(post("/api/v1/allocations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(tooManyHours))
            .andExpect(status().isBadRequest());
    }
}
