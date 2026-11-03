package com.toppaload.project;

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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectAndTaskIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired(required = false)
    ProjectLookup projectLookup;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private UUID supervisorId;
    private UUID memberId;

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
    }

    @Test
    @WithMockUser(username = "supervisor@toppaload.local", roles = "SUPERVISOR")
    void supervisorCanCreateProjectAndTasksWithFullDetailsAndAudit() throws Exception {
        String projectJson = """
            {
                "code": "PRJ-ALPHA",
                "name": "Project Alpha",
                "description": "Alpha project description",
                "status": "ACTIVE",
                "startDate": "2026-11-01",
                "endDate": "2026-12-31"
            }
        """;

        MvcResult projectResult = mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PRJ-ALPHA"))
                .andExpect(jsonPath("$.name").value("Project Alpha"))
                .andExpect(jsonPath("$.description").value("Alpha project description"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.startDate").value("2026-11-01"))
                .andExpect(jsonPath("$.endDate").value("2026-12-31"))
                .andExpect(jsonPath("$.ownerId").value(supervisorId.toString()))
                .andExpect(jsonPath("$.ownerName").value("Supervisor User"))
                .andExpect(jsonPath("$.taskCount").value(0))
                .andReturn();

        JsonNode projectNode = objectMapper.readTree(projectResult.getResponse().getContentAsString());
        String projectId = projectNode.get("id").asText();
        assertThat(projectId).isNotBlank();

        Integer projectAuditCount = jdbc.sql("""
            SELECT COUNT(*) FROM audit_logs
            WHERE action = 'CREATE' AND entity_type = 'PROJECT' AND entity_id = :id
        """).param("id", projectId).query(Integer.class).single();
        assertThat(projectAuditCount).isEqualTo(1);

        String taskJson = String.format("""
            {
                "title": "Initial Task",
                "description": "Task details",
                "status": "IN_PROGRESS",
                "priority": "HIGH",
                "estimatedHours": 16.5,
                "startDate": "2026-11-05",
                "dueDate": "2026-11-20",
                "progressPercent": 50,
                "assigneeId": "%s",
                "labels": ["backend", "core"]
            }
        """, memberId);

        MvcResult taskResult = mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.projectCode").value("PRJ-ALPHA"))
                .andExpect(jsonPath("$.projectName").value("Project Alpha"))
                .andExpect(jsonPath("$.title").value("Initial Task"))
                .andExpect(jsonPath("$.description").value("Task details"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.estimatedHours").value(16.5))
                .andExpect(jsonPath("$.startDate").value("2026-11-05"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-20"))
                .andExpect(jsonPath("$.progressPercent").value(50))
                .andExpect(jsonPath("$.assigneeId").value(memberId.toString()))
                .andExpect(jsonPath("$.assigneeName").value("Member User"))
                .andExpect(jsonPath("$.labels", containsInAnyOrder("backend", "core")))
                .andReturn();

        JsonNode taskNode = objectMapper.readTree(taskResult.getResponse().getContentAsString());
        String taskId = taskNode.get("id").asText();
        assertThat(taskId).isNotBlank();

        Integer taskAuditCount = jdbc.sql("""
            SELECT COUNT(*) FROM audit_logs
            WHERE action = 'CREATE' AND entity_type = 'TASK' AND entity_id = :id
        """).param("id", taskId).query(Integer.class).single();
        assertThat(taskAuditCount).isEqualTo(1);

        mockMvc.perform(get("/api/v1/projects/" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskCount").value(1));

        mockMvc.perform(get("/api/v1/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Initial Task"))
                .andExpect(jsonPath("$.progressPercent").value(50));
    }

    @Test
    @WithMockUser(username = "member@toppaload.local", roles = "MEMBER")
    void memberForbiddenFromCreatingProjectOrDeletingProjectReturns403() throws Exception {
        UUID prjId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-MEMBER', 'Project Member', '2026-11-01', '2026-12-01', :ownerId)
        """).param("id", prjId).param("ownerId", supervisorId).update();

        String projectJson = """
            {
                "code": "PRJ-FORBIDDEN",
                "name": "Forbidden Project",
                "startDate": "2026-11-01",
                "endDate": "2026-12-31"
            }
        """;

        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/projects/" + prjId)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@toppaload.local", roles = "MEMBER")
    void memberCanCreateAndUpdateTaskOnActiveProject() throws Exception {
        UUID prjId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-ACTIVE', 'Active Project', '2026-11-01', '2026-12-01', :ownerId)
        """).param("id", prjId).param("ownerId", supervisorId).update();

        String createJson = """
            {
                "title": "Member Created Task",
                "description": "By member",
                "status": "TODO",
                "priority": "MEDIUM",
                "estimatedHours": 4.0,
                "startDate": "2026-11-10",
                "dueDate": "2026-11-15",
                "progressPercent": 0,
                "labels": ["frontend"]
            }
        """;

        MvcResult taskResult = mockMvc.perform(post("/api/v1/projects/" + prjId + "/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Member Created Task"))
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(taskResult.getResponse().getContentAsString());
        String taskId = createdNode.get("id").asText();

        String updateJson = String.format("""
            {
                "title": "Member Updated Task",
                "description": "Updated by member",
                "status": "IN_PROGRESS",
                "priority": "HIGH",
                "estimatedHours": 6.0,
                "startDate": "2026-11-10",
                "dueDate": "2026-11-16",
                "progressPercent": 40,
                "assigneeId": "%s",
                "labels": ["frontend", "v1"]
            }
        """, memberId);

        mockMvc.perform(put("/api/v1/tasks/" + taskId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Member Updated Task"))
                .andExpect(jsonPath("$.progressPercent").value(40))
                .andExpect(jsonPath("$.labels", containsInAnyOrder("frontend", "v1")));

        Integer updateAudit = jdbc.sql("""
            SELECT COUNT(*) FROM audit_logs
            WHERE action = 'UPDATE' AND entity_type = 'TASK' AND entity_id = :id
        """).param("id", taskId).query(Integer.class).single();
        assertThat(updateAudit).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "supervisor@toppaload.local", roles = "SUPERVISOR")
    void rejectsProjectEndDateBeforeStartDateWith400ProblemDetail() throws Exception {
        String invalidJson = """
            {
                "code": "PRJ-INVALID-DATE",
                "name": "Invalid Date Project",
                "startDate": "2026-11-20",
                "endDate": "2026-11-10"
            }
        """;

        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Tanggal selesai tidak boleh sebelum tanggal mulai"));
    }

    @Test
    @WithMockUser(username = "supervisor@toppaload.local", roles = "SUPERVISOR")
    void rejectsDuplicateProjectCodeCaseInsensitiveWith409Conflict() throws Exception {
        UUID existingId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-DUP', 'Existing Duplicate', '2026-11-01', '2026-12-01', :ownerId)
        """).param("id", existingId).param("ownerId", supervisorId).update();

        String dupJson = """
            {
                "code": "prj-dup",
                "name": "Another Duplicate",
                "startDate": "2026-11-01",
                "endDate": "2026-12-01"
            }
        """;

        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dupJson))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Kode proyek sudah digunakan"));
    }

    @Test
    @WithMockUser(username = "supervisor@toppaload.local", roles = "SUPERVISOR")
    void rejectsTaskDueDateBeforeStartDateWith400ProblemDetail() throws Exception {
        UUID prjId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-TEST-DATE', 'Date Test', '2026-11-01', '2026-12-01', :ownerId)
        """).param("id", prjId).param("ownerId", supervisorId).update();

        String invalidTaskJson = """
            {
                "title": "Invalid Task Dates",
                "startDate": "2026-11-20",
                "dueDate": "2026-11-10"
            }
        """;

        mockMvc.perform(post("/api/v1/projects/" + prjId + "/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidTaskJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @WithMockUser(username = "supervisor@toppaload.local", roles = "SUPERVISOR")
    void rejectsTaskProgressPercentOutOfRangeWith400() throws Exception {
        UUID prjId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-TEST-PROG', 'Progress Test', '2026-11-01', '2026-12-01', :ownerId)
        """).param("id", prjId).param("ownerId", supervisorId).update();

        mockMvc.perform(post("/api/v1/projects/" + prjId + "/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "title": "Negative Progress",
                                "progressPercent": -5
                            }
                        """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/projects/" + prjId + "/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "title": "Over 100 Progress",
                                "progressPercent": 110
                            }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "supervisor@toppaload.local", roles = "SUPERVISOR")
    void softDeleteProjectHidesFromListAndCascadesToTasks() throws Exception {
        UUID prjId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-DEL', 'Delete Me', '2026-11-01', '2026-12-01', :ownerId)
        """).param("id", prjId).param("ownerId", supervisorId).update();

        UUID taskId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO tasks (id, project_id, title, status, priority, progress_percent)
            VALUES (:id, :projectId, 'Task to be cascaded', 'TODO', 'MEDIUM', 0)
        """).param("id", taskId).param("projectId", prjId).update();

        mockMvc.perform(delete("/api/v1/projects/" + prjId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + prjId + "')]").doesNotExist());

        mockMvc.perform(get("/api/v1/projects/" + prjId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projects/" + prjId + "/tasks"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/tasks/" + taskId))
                .andExpect(status().isNotFound());

        String prjDeletedAt = jdbc.sql("SELECT deleted_at FROM projects WHERE id = :id")
                .param("id", prjId).query(String.class).single();
        assertThat(prjDeletedAt).isNotNull();

        String taskDeletedAt = jdbc.sql("SELECT deleted_at FROM tasks WHERE id = :id")
                .param("id", taskId).query(String.class).single();
        assertThat(taskDeletedAt).isNotNull();

        Integer deleteAudit = jdbc.sql("""
            SELECT COUNT(*) FROM audit_logs
            WHERE action = 'DELETE' AND entity_type = 'PROJECT' AND entity_id = :id
        """).param("id", prjId.toString()).query(Integer.class).single();
        assertThat(deleteAudit).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "supervisor@toppaload.local", roles = "SUPERVISOR")
    void projectLookupFacadeWorksAcrossModules() {
        assertThat(projectLookup).isNotNull();

        ProjectSummary created = projectLookup.findOrCreateActiveByCode(
                "PRJ-LOOKUP",
                "Lookup Project",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 12, 1),
                supervisorId
        );
        assertThat(created).isNotNull();
        assertThat(created.code()).isEqualTo("PRJ-LOOKUP");

        ProjectSummary retrieved = projectLookup.requireActiveProject(created.id());
        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.name()).isEqualTo("Lookup Project");

        ProjectSummary same = projectLookup.findOrCreateActiveByCode(
                "prj-lookup",
                "Different Name",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 12, 1),
                supervisorId
        );
        assertThat(same.id()).isEqualTo(created.id());

        UUID nonExistent = UUID.randomUUID();
        Optional<TaskSummary> missingTask = projectLookup.findActiveTask(nonExistent);
        assertThat(missingTask).isEmpty();
    }
}
