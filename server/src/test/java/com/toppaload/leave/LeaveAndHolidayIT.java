package com.toppaload.leave;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LeaveAndHolidayIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private UUID supervisorId;
    private UUID memberId;

    @BeforeEach
    void setup() {
        jdbc.sql("DELETE FROM audit_logs").update();
        jdbc.sql("DELETE FROM leaves").update();
        jdbc.sql("DELETE FROM holidays").update();
        jdbc.sql("DELETE FROM users WHERE email NOT IN ('admin@toppaload.local')").update();

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
    void importHolidaysIsIdempotentAndUpdatesExistingEntries() throws Exception {
        String initialJson = """
            [
                {
                    "holidayDate": "2026-01-01",
                    "name": "Tahun Baru Masehi",
                    "scope": "NATIONAL"
                },
                {
                    "holidayDate": "2026-05-01",
                    "name": "Hari Buruh Internasional",
                    "scope": "NATIONAL"
                }
            ]
        """;

        mockMvc.perform(post("/api/v1/holidays/import")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2));

        mockMvc.perform(get("/api/v1/holidays").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].holidayDate").value("2026-01-01"))
                .andExpect(jsonPath("$[0].name").value("Tahun Baru Masehi"))
                .andExpect(jsonPath("$[1].holidayDate").value("2026-05-01"))
                .andExpect(jsonPath("$[1].name").value("Hari Buruh Internasional"));

        // Re-import with updated name and 1 additional date
        String updatedJson = """
            [
                {
                    "holidayDate": "2026-01-01",
                    "name": "Tahun Baru 2026 Masehi",
                    "scope": "NATIONAL"
                },
                {
                    "holidayDate": "2026-05-01",
                    "name": "Hari Buruh Sedunia",
                    "scope": "NATIONAL"
                },
                {
                    "holidayDate": "2026-08-17",
                    "name": "Hari Kemerdekaan RI",
                    "scope": "NATIONAL"
                }
            ]
        """;

        mockMvc.perform(post("/api/v1/holidays/import")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(3));

        mockMvc.perform(get("/api/v1/holidays").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].holidayDate").value("2026-01-01"))
                .andExpect(jsonPath("$[0].name").value("Tahun Baru 2026 Masehi"))
                .andExpect(jsonPath("$[1].holidayDate").value("2026-05-01"))
                .andExpect(jsonPath("$[1].name").value("Hari Buruh Sedunia"))
                .andExpect(jsonPath("$[2].holidayDate").value("2026-08-17"))
                .andExpect(jsonPath("$[2].name").value("Hari Kemerdekaan RI"));

        // CSV import test
        String csvData = """
            tanggal,nama,cakupan
            2026-12-25,Hari Raya Natal,NATIONAL
        """;

        mockMvc.perform(post("/api/v1/holidays/import")
                        .with(csrf())
                        .contentType("text/csv")
                        .content(csvData))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        mockMvc.perform(get("/api/v1/holidays").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    @WithMockUser(username = "member@toppaload.local", roles = "MEMBER")
    void memberCanCreateFullAndHalfDayLeaveForSelf() throws Exception {
        // Full day leave
        String fullDayLeaveJson = String.format("""
            {
                "userId": "%s",
                "leaveType": "ANNUAL",
                "portion": "FULL",
                "startDate": "2026-11-10",
                "endDate": "2026-11-12",
                "notes": "Liburan keluarga"
            }
        """, memberId);

        MvcResult fullResult = mockMvc.perform(post("/api/v1/leaves")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullDayLeaveJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(memberId.toString()))
                .andExpect(jsonPath("$.userEmail").value("member@toppaload.local"))
                .andExpect(jsonPath("$.userFullName").value("Member User"))
                .andExpect(jsonPath("$.leaveType").value("ANNUAL"))
                .andExpect(jsonPath("$.portion").value("FULL"))
                .andExpect(jsonPath("$.startDate").value("2026-11-10"))
                .andExpect(jsonPath("$.endDate").value("2026-11-12"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.notes").value("Liburan keluarga"))
                .andReturn();

        // Half day AM leave
        String halfAmLeaveJson = String.format("""
            {
                "userId": "%s",
                "leaveType": "SICK",
                "portion": "HALF_AM",
                "startDate": "2026-11-13",
                "endDate": "2026-11-13",
                "notes": "Pagi berobat"
            }
        """, memberId);

        mockMvc.perform(post("/api/v1/leaves")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(halfAmLeaveJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.portion").value("HALF_AM"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.startDate").value("2026-11-13"))
                .andExpect(jsonPath("$.endDate").value("2026-11-13"));

        // Half day PM leave
        String halfPmLeaveJson = String.format("""
            {
                "userId": "%s",
                "leaveType": "OTHER",
                "portion": "HALF_PM",
                "startDate": "2026-11-16",
                "endDate": "2026-11-16",
                "notes": "Siang ada urusan"
            }
        """, memberId);

        mockMvc.perform(post("/api/v1/leaves")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(halfPmLeaveJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.portion").value("HALF_PM"))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Query listing
        mockMvc.perform(get("/api/v1/leaves")
                        .param("userId", memberId.toString())
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].startDate").value("2026-11-10"))
                .andExpect(jsonPath("$[1].startDate").value("2026-11-13"))
                .andExpect(jsonPath("$[2].startDate").value("2026-11-16"));

        Integer auditCount = jdbc.sql("""
            SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'LEAVE' AND action = 'CREATE'
        """).query(Integer.class).single();
        assertThat(auditCount).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = "member@toppaload.local", roles = "MEMBER")
    void memberForbiddenFromCreatingLeaveForOtherUserReturns403() throws Exception {
        String leaveForSupervisorJson = String.format("""
            {
                "userId": "%s",
                "leaveType": "ANNUAL",
                "portion": "FULL",
                "startDate": "2026-11-10",
                "endDate": "2026-11-12",
                "notes": "Cuti untuk orang lain"
            }
        """, supervisorId);

        mockMvc.perform(post("/api/v1/leaves")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leaveForSupervisorJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Anggota hanya dapat mengajukan cuti untuk diri sendiri"));
    }

    @Test
    @WithMockUser(username = "member@toppaload.local", roles = "MEMBER")
    void halfDayLeaveWithMultiDayRangeIsRejectedWith400ProblemDetail() throws Exception {
        String invalidHalfDayLeaveJson = String.format("""
            {
                "userId": "%s",
                "leaveType": "ANNUAL",
                "portion": "HALF_PM",
                "startDate": "2026-11-04",
                "endDate": "2026-11-05",
                "notes": "Setengah hari multi-hari"
            }
        """, memberId);

        mockMvc.perform(post("/api/v1/leaves")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidHalfDayLeaveJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cuti setengah hari hanya boleh diajukan untuk satu tanggal"));

        // Also test endDate < startDate
        String invalidDateOrderJson = String.format("""
            {
                "userId": "%s",
                "leaveType": "ANNUAL",
                "portion": "FULL",
                "startDate": "2026-11-10",
                "endDate": "2026-11-05"
            }
        """, memberId);

        mockMvc.perform(post("/api/v1/leaves")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidDateOrderJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "member@toppaload.local", roles = "MEMBER")
    void deleteLeaveSoftDeletesAndHidesFromListing() throws Exception {
        String leaveJson = String.format("""
            {
                "userId": "%s",
                "leaveType": "ANNUAL",
                "portion": "FULL",
                "startDate": "2026-11-20",
                "endDate": "2026-11-20",
                "notes": "Mau cuti"
            }
        """, memberId);

        MvcResult createResult = mockMvc.perform(post("/api/v1/leaves")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leaveJson))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String leaveId = node.get("id").asText();

        // Member deletes their own leave -> 204
        mockMvc.perform(delete("/api/v1/leaves/" + leaveId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Check it is hidden from listing
        mockMvc.perform(get("/api/v1/leaves").param("userId", memberId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Check DB has deleted_at NOT NULL
        String deletedAt = jdbc.sql("SELECT deleted_at FROM leaves WHERE id = :id")
                .param("id", UUID.fromString(leaveId))
                .query(String.class)
                .single();
        assertThat(deletedAt).isNotBlank();

        Integer deleteAuditCount = jdbc.sql("""
            SELECT COUNT(*) FROM audit_logs
            WHERE entity_type = 'LEAVE' AND action = 'DELETE' AND entity_id = :id
        """).param("id", leaveId).query(Integer.class).single();
        assertThat(deleteAuditCount).isEqualTo(1);
    }
}
