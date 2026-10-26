package com.codereviewer.controller;

import com.codereviewer.dto.ReviewRequest;
import com.codereviewer.model.ReviewResult;
import com.codereviewer.model.codeIssue;
import com.codereviewer.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * POST /api/reviews
     * Returns a deliberately simple response DTO rather than serializing the
     * entire ReviewResult/codeIssue object graph. This avoids serialization
     * problems and gives the frontend predictable JSON fields.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> reviewCode(
            @RequestBody ReviewRequest request) {

        if (request == null || request.getSourceCode() == null
                || request.getSourceCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "sourceCode is required"
            ));
        }

        System.out.println("[API] Review request received");
        long startedAt = System.currentTimeMillis();

        ReviewResult result = reviewService.review(
                request.getSourceCode(), request.getFilename());

        System.out.println("[API] ReviewService completed in "
                + (System.currentTimeMillis() - startedAt) + " ms; building response");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filename", result.getFilename());
        response.put("aiReview", result.getAiReview());

        List<Map<String, Object>> issues = new ArrayList<>();
        if (result.getIssues() != null) {
            for (codeIssue issue : result.getIssues()) {
                if (issue == null) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("severity", issue.getSeverity());
                item.put("rule", issue.getRule());
                item.put("line", issue.getLine());
                item.put("message", issue.getMessage());
                issues.add(item);
            }
        }
        response.put("issues", issues);
        response.put("issueCount", issues.size());
        response.put("status", "completed");

        System.out.println("[API] Returning response with " + issues.size() + " issues");
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllReviews() {
        return ResponseEntity.ok(reviewService.getAllReviews());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getReviewById(@PathVariable("id") long id) {
        Map<String, Object> review = reviewService.getReviewById(id);
        if (review == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(review);
    }
}
