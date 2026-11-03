package com.toppaload.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toppaload.TestcontainersConfiguration;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LocalAuthIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Ensure bootstrap admin has password_hash set
        jdbc.sql("""
            UPDATE users
            SET password_hash = :hash, active = TRUE
            WHERE email = 'admin@toppaload.local'
        """)
        .param("hash", passwordEncoder.encode("admin123"))
        .update();
    }

    @Test
    void bootstrapAdminCanLoginWithDefaultPasswordAndAccessMe() throws Exception {
        Map<String, String> body = Map.of(
                "email", "admin@toppaload.local",
                "password", "admin123"
        );

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@toppaload.local"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        HttpSession session = loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // Access /api/v1/me using the authenticated session
        mockMvc.perform(get("/api/v1/me")
                        .session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@toppaload.local"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void rejectsIncorrectPassword() throws Exception {
        Map<String, String> body = Map.of(
                "email", "admin@toppaload.local",
                "password", "wrongpassword"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsNonExistentUser() throws Exception {
        Map<String, String> body = Map.of(
                "email", "unknown@company.com",
                "password", "anypassword"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInactiveUser() throws Exception {
        jdbc.sql("UPDATE users SET active = FALSE WHERE email = 'admin@toppaload.local'").update();

        Map<String, String> body = Map.of(
                "email", "admin@toppaload.local",
                "password", "admin123"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }
}

