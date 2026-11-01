package com.toppaload.shared;

import com.toppaload.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SpaForwardingIT {
    @Autowired MockMvc mockMvc;

    @Test
    void returnsSystemInfoJson() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("Toppa Load"))
                .andExpect(jsonPath("$.workTimezone").value("Asia/Jakarta"));
    }

    @Test
    void forwardsSpaDeepLinksToIndexHtml() throws Exception {
        mockMvc.perform(get("/beban-kerja"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void returns404ProblemDetailForUnknownApiPathAndMissingStaticAsset() throws Exception {
        mockMvc.perform(get("/api/v1/tidak-ada"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/assets/missing-chunk.js"))
                .andExpect(status().isNotFound());
    }
}

