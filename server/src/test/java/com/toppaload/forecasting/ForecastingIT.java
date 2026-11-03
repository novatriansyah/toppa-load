package com.toppaload.forecasting;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ForecastingIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private UUID adminId;
    private UUID memberId;

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

        memberId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
            VALUES (:id, 'dev@toppaload.local', 'Developer User', 'MEMBER', true, 8.00, '{1,2,3,4,5}')
        """).param("id", memberId).update();
    }

    private SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor auth(String email, String role) {
        return oidcLogin()
                .idToken(token -> token.claim("email", email).subject(email))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void scenarioFullLifecycle_create_allocate_simulate_and_promote() throws Exception {
        // 1. Create scenario
        CreateScenarioRequest createReq = new CreateScenarioRequest("Proyek Q1 2027", "Simulasi beban kerja proyek baru");
        MvcResult createResult = mockMvc.perform(post("/api/v1/scenarios")
                        .with(csrf())
                        .with(auth("admin@toppaload.local", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Proyek Q1 2027"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        ScenarioDto scenario = objectMapper.readValue(createResult.getResponse().getContentAsString(), ScenarioDto.class);
        UUID scenarioId = scenario.id();

        // 2. Add draft allocation
        LocalDate start = LocalDate.now().plusDays(7);
        LocalDate end = start.plusDays(14);
        AddScenarioAllocationRequest allocReq = new AddScenarioAllocationRequest(
                memberId,
                null,
                "New Mobile App",
                start,
                end,
                BigDecimal.valueOf(4.0),
                "Draft allocation note"
        );

        mockMvc.perform(post("/api/v1/scenarios/" + scenarioId + "/allocations")
                        .with(csrf())
                        .with(auth("admin@toppaload.local", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allocReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectName").value("New Mobile App"))
                .andExpect(jsonPath("$.hoursPerDay").value(4.0));

        // 3. Simulate scenario
        mockMvc.perform(post("/api/v1/scenarios/" + scenarioId + "/simulate")
                        .with(csrf())
                        .with(auth("admin@toppaload.local", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioId").value(scenarioId.toString()))
                .andExpect(jsonPath("$.userDeltas").isArray());

        // Check scenario status updated to SIMULATED
        ScenarioDto simulatedScenario = objectMapper.readValue(
                mockMvc.perform(get("/api/v1/scenarios/" + scenarioId)
                                .with(auth("admin@toppaload.local", "ADMIN")))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ScenarioDto.class
        );
        assertThat(simulatedScenario.status()).isEqualTo("SIMULATED");

        // 4. Promote scenario to production
        mockMvc.perform(post("/api/v1/scenarios/" + scenarioId + "/promote")
                        .with(csrf())
                        .with(auth("admin@toppaload.local", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotedAllocationsCount").value(1));

        // Verify allocation exists in allocations table
        int realAllocCount = jdbc.sql("SELECT COUNT(*) FROM allocations WHERE user_id = :userId")
                .param("userId", memberId)
                .query(Integer.class)
                .single();
        assertThat(realAllocCount).isEqualTo(1);

        // Verify scenario status is PROMOTED
        ScenarioDto promotedScenario = objectMapper.readValue(
                mockMvc.perform(get("/api/v1/scenarios/" + scenarioId)
                                .with(auth("admin@toppaload.local", "ADMIN")))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ScenarioDto.class
        );
        assertThat(promotedScenario.status()).isEqualTo("PROMOTED");
    }
}

