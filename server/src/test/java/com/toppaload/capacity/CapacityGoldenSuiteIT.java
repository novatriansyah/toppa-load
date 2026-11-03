package com.toppaload.capacity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toppaload.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CapacityGoldenSuiteIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private UUID userId;
    private UUID projectId;

    private final LocalDate monday = LocalDate.of(2026, 11, 2);
    private final LocalDate tuesday = LocalDate.of(2026, 11, 3);
    private final LocalDate wednesday = LocalDate.of(2026, 11, 4);
    private final LocalDate thursday = LocalDate.of(2026, 11, 5);
    private final LocalDate friday = LocalDate.of(2026, 11, 6);
    private final LocalDate sunday = LocalDate.of(2026, 11, 8);

    @BeforeEach
    void setup() {
        com.toppaload.test.TestDatabaseCleaner.cleanAll(jdbc);

        userId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
            VALUES (:id, 'worker@toppaload.local', 'Worker User', 'MEMBER', true, 8.00, '{1,2,3,4,5}')
        """).param("id", userId).update();

        projectId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, status, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-MAIN', 'Main Project', 'ACTIVE', '2026-01-01', '2026-12-31', :ownerId)
        """).param("id", projectId).param("ownerId", userId).update();
    }

    private void insertAllocation(LocalDate start, LocalDate end, double hoursPerDay, String type) {
        jdbc.sql("""
            INSERT INTO allocations (id, user_id, project_id, start_date, end_date, hours_per_day, allocation_type, created_by)
            VALUES (gen_random_uuid(), :userId, :projectId, :start, :end, :hours, :type, :userId)
        """)
        .param("userId", userId)
        .param("projectId", projectId)
        .param("start", start)
        .param("end", end)
        .param("hours", BigDecimal.valueOf(hoursPerDay))
        .param("type", type)
        .update();
    }

    private JsonNode fetchFirstCell(boolean includeTentative) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/capacity/heatmap")
                .param("from", monday.toString())
                .param("to", sunday.toString())
                .param("granularity", "WEEK")
                .param("includeTentative", String.valueOf(includeTentative)))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("rows")).isNotEmpty();
        for (JsonNode row : json.get("rows")) {
            if (row.get("userId").asText().equals(userId.toString())) {
                return row.get("cells").get(0);
            }
        }
        throw new IllegalStateException("Row for userId " + userId + " not found");
    }

    @Test
    @DisplayName("TC-01: 30.0 / 40.0j -> 75.0% OPTIMAL")
    @WithMockUser(username = "worker@toppaload.local")
    void tc01() throws Exception {
        insertAllocation(monday, friday, 6.00, "CONFIRMED");

        JsonNode cell = fetchFirstCell(false);
        assertThat(cell.get("netCapacityHours").asDouble()).isEqualTo(40.0);
        assertThat(cell.get("confirmedHours").asDouble()).isEqualTo(30.0);
        assertThat(cell.get("utilizationPct").asDouble()).isEqualTo(75.0);
        assertThat(cell.get("status").asText()).isEqualTo("OPTIMAL");
        assertThat(cell.get("hasLeaveConflict").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("TC-02: 20.0 / 40.0j -> 50.0% UNDERUTILIZED (< 60%)")
    @WithMockUser(username = "worker@toppaload.local")
    void tc02() throws Exception {
        insertAllocation(monday, friday, 4.00, "CONFIRMED");

        JsonNode cell = fetchFirstCell(false);
        assertThat(cell.get("netCapacityHours").asDouble()).isEqualTo(40.0);
        assertThat(cell.get("confirmedHours").asDouble()).isEqualTo(20.0);
        assertThat(cell.get("utilizationPct").asDouble()).isEqualTo(50.0);
        assertThat(cell.get("status").asText()).isEqualTo("UNDERUTILIZED");
    }

    @Test
    @DisplayName("TC-03: 24.0 / 40.0j -> 60.0% OPTIMAL (inclusive lower bound)")
    @WithMockUser(username = "worker@toppaload.local")
    void tc03() throws Exception {
        insertAllocation(monday, friday, 4.80, "CONFIRMED");

        JsonNode cell = fetchFirstCell(false);
        assertThat(cell.get("netCapacityHours").asDouble()).isEqualTo(40.0);
        assertThat(cell.get("confirmedHours").asDouble()).isEqualTo(24.0);
        assertThat(cell.get("utilizationPct").asDouble()).isEqualTo(60.0);
        assertThat(cell.get("status").asText()).isEqualTo("OPTIMAL");
    }

    @Test
    @DisplayName("TC-04: 40.0 / 40.0j -> 100.0% OPTIMAL (inclusive upper bound)")
    @WithMockUser(username = "worker@toppaload.local")
    void tc04() throws Exception {
        insertAllocation(monday, friday, 5.00, "CONFIRMED");
        insertAllocation(monday, friday, 3.00, "CONFIRMED");

        JsonNode cell = fetchFirstCell(false);
        assertThat(cell.get("netCapacityHours").asDouble()).isEqualTo(40.0);
        assertThat(cell.get("confirmedHours").asDouble()).isEqualTo(40.0);
        assertThat(cell.get("utilizationPct").asDouble()).isEqualTo(100.0);
        assertThat(cell.get("status").asText()).isEqualTo("OPTIMAL");
    }

    @Test
    @DisplayName("TC-05: 42.0 / 40.0j -> 105.0% OVERLOADED")
    @WithMockUser(username = "worker@toppaload.local")
    void tc05() throws Exception {
        insertAllocation(monday, friday, 6.00, "CONFIRMED");
        insertAllocation(wednesday, friday, 4.00, "CONFIRMED"); // 3 days * 4h = 12h

        JsonNode cell = fetchFirstCell(false);
        assertThat(cell.get("netCapacityHours").asDouble()).isEqualTo(40.0);
        assertThat(cell.get("confirmedHours").asDouble()).isEqualTo(42.0);
        assertThat(cell.get("utilizationPct").asDouble()).isEqualTo(105.0);
        assertThat(cell.get("status").asText()).isEqualTo("OVERLOADED");
    }

    @Test
    @DisplayName("TC-06: 32.0 / 32.0j -> 100.0% OPTIMAL (National holiday on Thursday)")
    @WithMockUser(username = "worker@toppaload.local")
    void tc06() throws Exception {
        jdbc.sql("""
            INSERT INTO holidays (id, holiday_date, name, scope)
            VALUES (gen_random_uuid(), :holidayDate, 'Hari Libur Nasional', 'NATIONAL')
        """).param("holidayDate", thursday).update();

        insertAllocation(monday, friday, 8.00, "CONFIRMED");

        JsonNode cell = fetchFirstCell(false);
        assertThat(cell.get("netCapacityHours").asDouble()).isEqualTo(32.0);
        assertThat(cell.get("confirmedHours").asDouble()).isEqualTo(32.0);
        assertThat(cell.get("utilizationPct").asDouble()).isEqualTo(100.0);
        assertThat(cell.get("status").asText()).isEqualTo("OPTIMAL");
    }

    @Test
    @DisplayName("TC-07: 40.0 / 32.0j -> 125.0% OVERLOADED (Leave on Friday + leave conflict)")
    @WithMockUser(username = "worker@toppaload.local")
    void tc07() throws Exception {
        jdbc.sql("""
            INSERT INTO leaves (id, user_id, leave_type, portion, start_date, end_date, status)
            VALUES (gen_random_uuid(), :userId, 'ANNUAL', 'FULL', :start, :end, 'APPROVED')
        """).param("userId", userId).param("start", friday).param("end", friday).update();

        insertAllocation(monday, friday, 8.00, "CONFIRMED");

        JsonNode cell = fetchFirstCell(false);
        assertThat(cell.get("netCapacityHours").asDouble()).isEqualTo(32.0);
        assertThat(cell.get("confirmedHours").asDouble()).isEqualTo(40.0);
        assertThat(cell.get("utilizationPct").asDouble()).isEqualTo(125.0);
        assertThat(cell.get("status").asText()).isEqualTo("OVERLOADED");
        assertThat(cell.get("hasLeaveConflict").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("TC-08: 30.0 / 36.0j -> 83.3% OPTIMAL (Half-day leave on Wednesday)")
    @WithMockUser(username = "worker@toppaload.local")
    void tc08() throws Exception {
        jdbc.sql("""
            INSERT INTO leaves (id, user_id, leave_type, portion, start_date, end_date, status)
            VALUES (gen_random_uuid(), :userId, 'ANNUAL', 'HALF_AM', :start, :end, 'APPROVED')
        """).param("userId", userId).param("start", wednesday).param("end", wednesday).update();

        insertAllocation(monday, friday, 6.00, "CONFIRMED");

        JsonNode cell = fetchFirstCell(false);
        assertThat(cell.get("netCapacityHours").asDouble()).isEqualTo(36.0);
        assertThat(cell.get("confirmedHours").asDouble()).isEqualTo(30.0);
        assertThat(cell.get("utilizationPct").asDouble()).isEqualTo(83.3);
        assertThat(cell.get("status").asText()).isEqualTo("OPTIMAL");
    }

    @Test
    @DisplayName("TC-09: 8.0 / 0.0j -> null% OVERLOADED with leave conflict (divide by zero safe)")
    @WithMockUser(username = "worker@toppaload.local")
    void tc09() throws Exception {
        jdbc.sql("""
            INSERT INTO leaves (id, user_id, leave_type, portion, start_date, end_date, status)
            VALUES (gen_random_uuid(), :userId, 'ANNUAL', 'FULL', :start, :end, 'APPROVED')
        """).param("userId", userId).param("start", monday).param("end", friday).update();

        insertAllocation(monday, tuesday, 4.00, "CONFIRMED"); // 2 days * 4h = 8h

        JsonNode cell = fetchFirstCell(false);
        assertThat(cell.get("netCapacityHours").asDouble()).isEqualTo(0.0);
        assertThat(cell.get("confirmedHours").asDouble()).isEqualTo(8.0);
        assertThat(cell.get("utilizationPct").isNull()).isTrue();
        assertThat(cell.get("status").asText()).isEqualTo("OVERLOADED");
        assertThat(cell.get("hasLeaveConflict").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("TC-10: Confirmed 30j + Tentative 20j (Default 75% vs IncludeTentative 125%)")
    @WithMockUser(username = "worker@toppaload.local")
    void tc10() throws Exception {
        insertAllocation(monday, friday, 6.00, "CONFIRMED");
        insertAllocation(monday, friday, 4.00, "TENTATIVE");

        // Default mode
        JsonNode defaultCell = fetchFirstCell(false);
        assertThat(defaultCell.get("netCapacityHours").asDouble()).isEqualTo(40.0);
        assertThat(defaultCell.get("confirmedHours").asDouble()).isEqualTo(30.0);
        assertThat(defaultCell.get("tentativeHours").asDouble()).isEqualTo(20.0);
        assertThat(defaultCell.get("utilizationPct").asDouble()).isEqualTo(75.0);
        assertThat(defaultCell.get("status").asText()).isEqualTo("OPTIMAL");

        // Include tentative mode
        JsonNode tentativeCell = fetchFirstCell(true);
        assertThat(tentativeCell.get("netCapacityHours").asDouble()).isEqualTo(40.0);
        assertThat(tentativeCell.get("utilizationPct").asDouble()).isEqualTo(125.0);
        assertThat(tentativeCell.get("status").asText()).isEqualTo("OVERLOADED");
    }

    @Test
    @DisplayName("User breakdown endpoint returns day-by-day details with active allocations")
    @WithMockUser(username = "worker@toppaload.local")
    void userBreakdownEndpoint() throws Exception {
        insertAllocation(monday, friday, 6.00, "CONFIRMED");

        mockMvc.perform(get("/api/v1/capacity/users/" + userId + "/breakdown")
                .param("from", monday.toString())
                .param("to", sunday.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(7))
            .andExpect(jsonPath("$[0].date").value("2026-11-02"))
            .andExpect(jsonPath("$[0].isWorkday").value(true))
            .andExpect(jsonPath("$[0].confirmedHours").value(6.0))
            .andExpect(jsonPath("$[0].allocations").isArray())
            .andExpect(jsonPath("$[0].allocations.length()").value(1));
    }

    @Test
    @DisplayName("Allocation preview endpoint detects newly introduced overload")
    @WithMockUser(username = "worker@toppaload.local")
    void previewAllocationEndpoint() throws Exception {
        insertAllocation(monday, friday, 6.00, "CONFIRMED"); // 30h

        String previewRequest = """
            {
                "userId": "%s",
                "startDate": "%s",
                "endDate": "%s",
                "hoursPerDay": 4.00,
                "allocationType": "CONFIRMED"
            }
        """.formatted(userId, monday, friday); // adding 20h -> total 50h > 40h

        mockMvc.perform(post("/api/v1/allocations/preview")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(previewRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.introducesOverload").value(true))
            .andExpect(jsonPath("$.afterCells[0].status").value("OVERLOADED"));
    }
}

