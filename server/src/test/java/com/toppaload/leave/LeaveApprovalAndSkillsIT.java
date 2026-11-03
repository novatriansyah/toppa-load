package com.toppaload.leave;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
class LeaveApprovalAndSkillsIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private UUID supervisorId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.cleanAll(jdbc);

        supervisorId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, work_hours_per_day, work_days, active)
            VALUES (:id, 'spv.leave@toppaload.local', 'Supervisor Cuti', 'SUPERVISOR', 8.0, '{1,2,3,4,5}', true)
        """).param("id", supervisorId).update();

        memberId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, work_hours_per_day, work_days, active)
            VALUES (:id, 'member.cuti@toppaload.local', 'Bambang Cuti', 'MEMBER', 8.0, '{1,2,3,4,5}', true)
        """).param("id", memberId).update();
    }

    @Test
    @DisplayName("Pengajuan Cuti berstatus PENDING dan saat disetujui Supervisor memotong saldo cuti tahunan")
    void memberSubmitsLeavePendingAndSupervisorApprovesDeductingBalance() throws Exception {
        // 1. Member requests 2 days leave
        String leaveJson = """
            {
              "userId": "%s",
              "leaveType": "ANNUAL",
              "portion": "FULL",
              "startDate": "2026-11-04",
              "endDate": "2026-11-05",
              "notes": "Keperluan pribadi"
            }
        """.formatted(memberId);

        String responseBody = mockMvc.perform(post("/api/v1/leaves")
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.cuti@toppaload.local")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leaveJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();

        // 2. Initial balance: 12 days total, 0 used, 12 remaining
        mockMvc.perform(get("/api/v1/leaves/balances")
                        .param("year", "2026")
                        .param("userId", memberId.toString())
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.cuti@toppaload.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllowanceDays", closeTo(12.0, 0.01)))
                .andExpect(jsonPath("$.usedDays", closeTo(0.0, 0.01)))
                .andExpect(jsonPath("$.remainingDays", closeTo(12.0, 0.01)));

        // Extract leave id
        UUID leaveId = jdbc.sql("SELECT id FROM leaves WHERE user_id = :userId")
                .param("userId", memberId)
                .query(rs -> {
                    rs.next();
                    return UUID.fromString(rs.getString("id"));
                });

        // 3. Supervisor approves leave
        mockMvc.perform(post("/api/v1/leaves/{id}/approve", leaveId)
                        .with(oidcLogin().idToken(t -> t.claim("email", "spv.leave@toppaload.local"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));

        // 4. Verify balance now deducted (2 days used, 10 remaining)
        mockMvc.perform(get("/api/v1/leaves/balances")
                        .param("year", "2026")
                        .param("userId", memberId.toString())
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.cuti@toppaload.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedDays", closeTo(2.0, 0.01)))
                .andExpect(jsonPath("$.remainingDays", closeTo(10.0, 0.01)));
    }

    @Test
    @DisplayName("Anggota tim dapat mengelola dan mencari keahlian (Skill Matrix)")
    void memberCanManageAndSearchSkillMatrix() throws Exception {
        String skillsJson = """
            {
              "skills": [
                {
                  "skillName": "React 19",
                  "proficiencyLevel": "ADVANCED"
                },
                {
                  "skillName": "PostgreSQL",
                  "proficiencyLevel": "EXPERT"
                }
              ]
            }
        """;

        // 1. Update skills
        mockMvc.perform(put("/api/v1/users/{userId}/skills", memberId)
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.cuti@toppaload.local")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillsJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].skillName", containsInAnyOrder("React 19", "PostgreSQL")));

        // 2. Query skills directory
        mockMvc.perform(get("/api/v1/skills/directory")
                        .with(oidcLogin().idToken(t -> t.claim("email", "member.cuti@toppaload.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }
}

