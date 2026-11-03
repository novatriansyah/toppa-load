package com.toppaload.identity;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IdentitySecurityIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    UserDirectory userDirectory;

    @BeforeEach
    void setup() {
        jdbc.sql("DELETE FROM audit_logs").update();
        jdbc.sql("DELETE FROM users WHERE email != 'admin@toppaload.local'").update();
        Integer count = jdbc.sql("SELECT COUNT(*) FROM users WHERE email = 'admin@toppaload.local'").query(Integer.class).single();
        if (count == null || count == 0) {
            jdbc.sql("""
                INSERT INTO users (email, full_name, role, active, work_hours_per_day, work_days)
                VALUES ('admin@toppaload.local', 'Bootstrap Admin', 'ADMIN', true, 8.00, '{1,2,3,4,5}')
            """).update();
        }
    }

    @Test
    void unauthenticatedRequestToApiReturns401OrLoginRedirect() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users"))
                .andReturn();
        int status = result.getResponse().getStatus();
        assertThat(status).isIn(401, 302);
    }

    @Test
    @WithMockUser(username = "admin@toppaload.local", roles = "ADMIN")
    void bootstrapAdminExistsAndCanLogin() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@toppaload.local"))
                .andExpect(jsonPath("$.fullName").value("Bootstrap Admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.workHoursPerDay").value(8.0))
                .andExpect(jsonPath("$.workDays", hasSize(5)));
    }

    @Test
    @WithMockUser(username = "member@kantor.id", roles = "MEMBER")
    void memberRoleCannotCreateUserReturns403() throws Exception {
        jdbc.sql("""
            INSERT INTO users (email, full_name, role, active)
            VALUES ('member@kantor.id', 'Regular Member', 'MEMBER', true)
        """).update();

        String body = """
            {
                "email": "another@kantor.id",
                "fullName": "Another User",
                "role": "MEMBER"
            }
        """;

        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@toppaload.local", roles = "ADMIN")
    void adminCreateUserWithoutCsrfTokenReturns403Forbidden() throws Exception {
        String body = """
            {
                "email": "no-csrf@kantor.id",
                "fullName": "No Csrf User",
                "role": "MEMBER"
            }
        """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@kantor.id", roles = "MEMBER")
    void memberRoleCannotAccessAuditLogsReturns403() throws Exception {
        jdbc.sql("""
            INSERT INTO users (email, full_name, role, active)
            VALUES ('member@kantor.id', 'Regular Member', 'MEMBER', true)
        """).update();

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@toppaload.local", roles = "ADMIN")
    void adminCanCreateAndPatchUserWithAuditLog() throws Exception {
        String createJson = """
            {
                "email": "stefan@kantor.id",
                "fullName": "Stefan Kowalski",
                "role": "SUPERVISOR",
                "workHoursPerDay": 7.50,
                "workDays": [1, 2, 3, 4]
            }
        """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("stefan@kantor.id"))
                .andExpect(jsonPath("$.fullName").value("Stefan Kowalski"))
                .andExpect(jsonPath("$.role").value("SUPERVISOR"))
                .andExpect(jsonPath("$.workHoursPerDay").value(7.50))
                .andExpect(jsonPath("$.workDays", containsInAnyOrder(1, 2, 3, 4)))
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String userId = createdNode.get("id").asText();
        assertThat(userId).isNotBlank();

        Integer createAuditCount = jdbc.sql("""
            SELECT COUNT(*) FROM audit_logs
            WHERE action = 'CREATE' AND entity_type = 'USER' AND entity_id = :id
        """).param("id", userId).query(Integer.class).single();
        assertThat(createAuditCount).isEqualTo(1);

        String patchJson = """
            {
                "fullName": "Stefan Senior",
                "workHoursPerDay": 8.00,
                "workDays": [1, 2, 3, 4, 5]
            }
        """;

        mockMvc.perform(patch("/api/v1/users/" + userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Stefan Senior"))
                .andExpect(jsonPath("$.workHoursPerDay").value(8.00))
                .andExpect(jsonPath("$.workDays", hasSize(5)));

        String beforeName = jdbc.sql("""
            SELECT before_data->>'fullName' FROM audit_logs
            WHERE action = 'UPDATE' AND entity_type = 'USER' AND entity_id = :id
        """).param("id", userId).query(String.class).single();
        assertThat(beforeName).isEqualTo("Stefan Kowalski");

        String afterName = jdbc.sql("""
            SELECT after_data->>'fullName' FROM audit_logs
            WHERE action = 'UPDATE' AND entity_type = 'USER' AND entity_id = :id
        """).param("id", userId).query(String.class).single();
        assertThat(afterName).isEqualTo("Stefan Senior");
    }

    @Test
    @WithMockUser(username = "admin@toppaload.local", roles = "ADMIN")
    void patchUserWithInvalidWorkDaysReturns400ProblemDetail() throws Exception {
        UUID targetId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active)
            VALUES (:id, 'worker@kantor.id', 'Worker', 'MEMBER', true)
        """).param("id", targetId).update();

        mockMvc.perform(patch("/api/v1/users/" + targetId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "workDays": [0, 8] }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(patch("/api/v1/users/" + targetId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "workDays": [] }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @WithMockUser(username = "active-then-disabled@kantor.id", roles = "MEMBER")
    void deactivatedUserDuringActiveSessionIsRejectedWith403() throws Exception {
        jdbc.sql("""
            INSERT INTO users (email, full_name, role, active)
            VALUES ('active-then-disabled@kantor.id', 'Active User', 'MEMBER', true)
        """).update();

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("active-then-disabled@kantor.id"));

        jdbc.sql("UPDATE users SET active = false WHERE email = 'active-then-disabled@kantor.id'").update();

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void oidcLoginAllowsActiveUserAndExtractsProfile() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .with(oidcLogin().idToken(token -> token
                                .claim("sub", "admin-sub")
                                .claim("email", "admin@toppaload.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@toppaload.local"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void oidcLoginRejectsUnlistedUserWith403() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .with(oidcLogin().idToken(token -> token
                                .claim("sub", "stranger-sub")
                                .claim("email", "stranger@external.com"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @WithMockUser(username = "admin@toppaload.local", roles = "ADMIN")
    void listsActiveUsers() throws Exception {
        jdbc.sql("""
            INSERT INTO users (email, full_name, role, active)
            VALUES ('alpha@kantor.id', 'Alpha User', 'MEMBER', true)
        """).update();

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // admin + alpha
                .andExpect(jsonPath("$[0].email").isNotEmpty());
    }
}
