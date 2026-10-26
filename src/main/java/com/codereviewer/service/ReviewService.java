package com.codereviewer.service;

import com.codereviewer.ai.AIReviewer;
import com.codereviewer.analyzer.CodeReviewer;
import com.codereviewer.database.ReviewRepository;
import com.codereviewer.model.ReviewResult;
import com.codereviewer.utils.JsonReportGeneration;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private final CodeReviewer codeReviewer;
    private final AIReviewer aiReviewer;
    private final ReviewRepository reviewRepository;
    private final JsonReportGeneration jsonReportGeneration;

    public ReviewService(ReviewRepository reviewRepository) {
        this.codeReviewer = new CodeReviewer();
        this.aiReviewer = new AIReviewer();
        this.reviewRepository = reviewRepository;
        this.jsonReportGeneration = new JsonReportGeneration();
    }

    /**
     * Reviews source code, attaches the AI output to the result,
     * persists the result, and returns the same result to the controller.
     */
    public ReviewResult review(String sourceCode, String filename) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("Source code cannot be empty.");
        }

        String safeFilename = (filename == null || filename.isBlank())
                ? "Untitled.java"
                : filename.trim();

        // 1. Run static analysis.
        ReviewResult result = codeReviewer.review(sourceCode, safeFilename);
        if (result == null) {
            throw new IllegalStateException(
                    "Static analysis did not return a ReviewResult."
            );
        }

        // 2. Generate AI review. Do not continue silently with an empty result
        // if Ollama fails; return a clear error to the controller/client.
        List<com.codereviewer.model.codeIssue> issues = result.getIssues();
        String aiReview = aiReviewer.review(sourceCode, issues);

        if (aiReview == null || aiReview.isBlank()) {
            throw new IllegalStateException(
                    "Ollama returned an empty AI review. Check the Ollama model and logs."
            );
        }

        // 3. Attach the AI text before saving or returning the object.
        result.setAiReview(aiReview);

        // 4. Persist the complete result.
        reviewRepository.saveReview(result, safeFilename, aiReview);

        // 5. Write the same complete result to the JSON report.
        jsonReportGeneration.generateReport(result);

        // 6. The controller should return this object as the HTTP response body.
        return result;
    }

    public List<Map<String, Object>> getAllReviews() {
        return reviewRepository.getAllReviews();
    }

    public Map<String, Object> getReviewById(long id) {
        return reviewRepository.getReviewById(id);
    }
}
