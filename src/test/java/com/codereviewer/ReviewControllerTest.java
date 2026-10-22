package com.codereviewer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/reviews should return list of reviews from SQLite database")
    void testGetAllReviewsEndpoint() throws Exception {
        mockMvc.perform(get("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/reviews should analyze code, call AI reviewer, and return report")
    void testPostReviewEndpoint() throws Exception {
        String requestJson = """
                {
                    "filename": "SafeService.java",
                    "sourceCode": "public class SafeService { public String greet(String name) { return \\"Hello \\" + name; } }"
                }
                """;

        mockMvc.perform(post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename", is("SafeService.java")))
                .andExpect(jsonPath("$.aiReview", notNullValue()))
                .andExpect(jsonPath("$.reviewDate", notNullValue()));
    }
}
