package com.toppaload.calendar;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CalendarFeedIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private UUID userId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.cleanAll(jdbc);

        userId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO users (id, email, full_name, role, active, work_hours_per_day, work_days)
            VALUES (:id, 'worker@toppaload.local', 'Calendar Worker', 'MEMBER', true, 8.00, '{1,2,3,4,5}')
        """).param("id", userId).update();

        projectId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO projects (id, code, name, description, status, start_date, end_date, owner_id)
            VALUES (:id, 'PRJ-CAL', 'Calendar Project', 'Calendar Project Desc', 'ACTIVE', '2026-11-01', '2026-11-30', :ownerId)
        """)
        .param("id", projectId)
        .param("ownerId", userId)
        .update();

        LocalDate today = LocalDate.now();
        jdbc.sql("""
            INSERT INTO allocations (id, project_id, user_id, start_date, end_date, hours_per_day, allocation_type, created_by)
            VALUES (gen_random_uuid(), :projId, :userId, :start, :end, 5.0, 'CONFIRMED', :userId)
        """)
        .param("projId", projectId)
        .param("userId", userId)
        .param("start", today)
        .param("end", today.plusDays(5))
        .update();

        jdbc.sql("""
            INSERT INTO leaves (id, user_id, start_date, end_date, leave_type, portion, reason, status)
            VALUES (gen_random_uuid(), :userId, :start, :end, 'ANNUAL', 'FULL', 'Liburan Keluarga', 'APPROVED')
        """)
        .param("userId", userId)
        .param("start", today.plusDays(10))
        .param("end", today.plusDays(11))
        .update();
    }

    private SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor auth(String email, String role) {
        return oidcLogin()
                .idToken(token -> token.claim("email", email).subject(email))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void calendarFeedLifecycle_getToken_accessFeedPublicly_and_rotate() throws Exception {
        // 1. Get or create feed token
        MvcResult tokenResult = mockMvc.perform(get("/api/v1/calendar/token")
                        .with(auth("worker@toppaload.local", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.feedUrl").isString())
                .andReturn();

        CalendarFeedTokenDto tokenDto = objectMapper.readValue(tokenResult.getResponse().getContentAsString(), CalendarFeedTokenDto.class);
        String token = tokenDto.token();
        assertThat(token).hasSize(64);

        // 2. Fetch public .ics stream (NO AUTHENTICATION REQUIRED)
        MvcResult feedResult = mockMvc.perform(get("/api/v1/calendar/feed/" + token + ".ics"))
                .andExpect(status().isOk())
                .andReturn();

        String icsContent = feedResult.getResponse().getContentAsString();
        assertThat(icsContent).contains("BEGIN:VCALENDAR");
        assertThat(icsContent).contains("PRODID:-//Toppa Load//Team Capacity Calendar//ID");
        assertThat(icsContent).contains("Calendar Project");
        assertThat(icsContent).contains("jam/hari");
        assertThat(icsContent).contains("Liburan Keluarga");
        assertThat(icsContent).contains("END:VCALENDAR");

        // 3. Rotate token
        MvcResult rotateResult = mockMvc.perform(post("/api/v1/calendar/token/rotate")
                        .with(csrf())
                        .with(auth("worker@toppaload.local", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        CalendarFeedTokenDto rotatedDto = objectMapper.readValue(rotateResult.getResponse().getContentAsString(), CalendarFeedTokenDto.class);
        assertThat(rotatedDto.token()).isNotEqualTo(token);

        // 4. Old token now returns 404
        mockMvc.perform(get("/api/v1/calendar/feed/" + token + ".ics"))
                .andExpect(status().isNotFound());

        // 5. New token works
        mockMvc.perform(get("/api/v1/calendar/feed/" + rotatedDto.token() + ".ics"))
                .andExpect(status().isOk());
    }
}

