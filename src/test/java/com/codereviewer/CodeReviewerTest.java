package com.codereviewer;

import com.codereviewer.ai.AIReviewer;
import com.codereviewer.analyzer.CodeReviewer;
import com.codereviewer.database.DatabaseInitializer;
import com.codereviewer.database.DatabaseManager;
import com.codereviewer.database.ReviewRepository;
import com.codereviewer.model.ReviewResult;
import com.codereviewer.service.ReviewService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CodeReviewerTest {

    @BeforeAll
    static void setupDatabase() {
        DatabaseInitializer initializer = new DatabaseInitializer();
        initializer.initializeDatabase();
    }

    @Test
    @DisplayName("Database connection should be valid and open")
    void testDatabaseConnection() throws Exception {
        try (Connection conn = DatabaseManager.getConnection()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
        }
    }

    @Test
    @DisplayName("AST Analyzer should detect SQL Injection in raw concatenation")
    void testSqlInjectionDetection() {
        String vulnerableSqlCode = """
                public class UserRepository {
                    public void findUser(String name) {
                        String query = "SELECT * FROM users WHERE name = '" + name + "'";
                    }
                }
                """;

        CodeReviewer reviewer = new CodeReviewer();
        ReviewResult result = reviewer.review(vulnerableSqlCode, "UserRepository.java");

        assertNotNull(result);
        assertFalse(result.getIssues().isEmpty(), "Expected issues to be detected");

        boolean hasSqlIssue = result.getIssues().stream()
                .anyMatch(issue -> "JAVA013".equalsIgnoreCase(issue.getRule()) 
                        || issue.getMessage().toLowerCase().contains("sql"));
        assertTrue(hasSqlIssue, "SQL Injection analyzer should have flagged vulnerable query");
    }

    @Test
    @DisplayName("AIReviewer should be configured and connect to local Ollama")
    void testAIReviewerConnection() {
        AIReviewer aiReviewer = new AIReviewer();
        assertNotNull(aiReviewer.getBaseUrl());
        assertNotNull(aiReviewer.getModel());
        assertTrue(aiReviewer.getTimeoutSeconds() > 0);

        // Verify Ollama health check
        boolean online = aiReviewer.isConfigured();
        System.out.println("Ollama online status: " + online);
        assertTrue(online, "Local Ollama server should be responding at " + aiReviewer.getBaseUrl());
    }

    @Test
    @DisplayName("End-to-end ReviewService should analyze, review, and persist to SQLite")
    void testEndToEndReviewPipeline() {
        ReviewRepository repository = new ReviewRepository();
        ReviewService service = new ReviewService(repository);

        String sampleCode = """
                public class MathHelper {
                    public int add(int a, int b) {
                        return a + b;
                    }
                }
                """;

        ReviewResult result = service.review(sampleCode, "MathHelper.java");

        assertNotNull(result);
        assertEquals("MathHelper.java", result.getFilename());
        assertNotNull(result.getAiReview(), "AI review should be populated");
        assertFalse(result.getAiReview().isBlank(), "AI review should not be empty");

        // Verify saved to database
        List<Map<String, Object>> allReviews = service.getAllReviews();
        assertFalse(allReviews.isEmpty(), "Database should contain at least one review");

        Map<String, Object> latestReview = allReviews.get(0);
        assertNotNull(latestReview.get("id"));
        assertNotNull(latestReview.get("fileName"));
    }
}
