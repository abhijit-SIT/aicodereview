package com.codereviewer.service;

import com.codereviewer.ai.AIReviewer;
import com.codereviewer.analyzer.CodeReviewer;
import com.codereviewer.database.ReviewRepository;
import com.codereviewer.model.ReviewResult;
import com.codereviewer.model.codeIssue;
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

    public ReviewService(
            ReviewRepository reviewRepository) {

        this.codeReviewer =
                new CodeReviewer();

        this.aiReviewer =
                new AIReviewer();

        this.reviewRepository =
                reviewRepository;

        this.jsonReportGeneration =
                new JsonReportGeneration();
    }


    // =====================================================
    // CREATE REVIEW
    // =====================================================

    public ReviewResult review(
            String sourceCode,
            String filename) {

        // 1. Static analysis
        ReviewResult result =
                codeReviewer.review(
                        sourceCode,
                        filename
                );

        // 2. Compute scores from detected issues
        computeScores(result);

        // 3. AI review (with graceful fallback if Ollama is unavailable)
        String aiReview;
        try {
            if (aiReviewer.isConfigured()) {
                aiReview = aiReviewer.review(
                        sourceCode,
                        result.getIssues()
                );
            } else {
                aiReview = "ℹ️ AI review skipped: Ollama server is offline or unreachable at "
                        + aiReviewer.getBaseUrl()
                        + ". Static AST code analysis completed successfully.";
            }
        } catch (Exception e) {
            aiReview = "⚠️ AI review could not be completed: "
                    + e.getMessage()
                    + ". Static AST code analysis completed successfully.";
        }

        // 4. Add AI review
        result.setAiReview(
                aiReview
        );

        // 5. Save to database
        reviewRepository.saveReview(
                result,
                filename,
                aiReview
        );

        // 6. Save to JSON history
        jsonReportGeneration.generateReport(
                result
        );

        return result;
    }


    // =====================================================
    // COMPUTE SCORES
    // Per-severity deduction weights matching the frontend logic
    // =====================================================

    private void computeScores(ReviewResult result) {

        List<codeIssue> issues = result.getIssues();

        double secDeductions  = 0;
        double qualDeductions = 0;
        double maintDeductions = 0;

        for (codeIssue issue : issues) {

            double deduction = severityWeight(issue.getSeverity());

            switch (issue.getCategory()) {
                case "security":
                    secDeductions += deduction;
                    break;
                case "quality":
                    qualDeductions += deduction;
                    break;
                case "maintainability":
                    maintDeductions += deduction;
                    break;
                default:
                    qualDeductions += deduction;
            }
        }

        double secScore   = clamp(10.0 - secDeductions);
        double qualScore  = clamp(10.0 - qualDeductions);
        double maintScore = clamp(10.0 - maintDeductions);

        double overallScore = Math.floor(
                ((secScore + qualScore + maintScore) / 3.0) * 10) / 10.0;

        String verdict;
        if (overallScore < 5.0) {
            verdict = "Critical Risks Detected";
        } else if (overallScore < 7.5) {
            verdict = "Refactoring Recommended";
        } else {
            verdict = "Production Ready";
        }

        result.setSecScore(round1(secScore));
        result.setQualScore(round1(qualScore));
        result.setMaintScore(round1(maintScore));
        result.setOverallScore(round1(overallScore));
        result.setVerdict(verdict);
    }

    private double severityWeight(String severity) {
        if (severity == null) return 1.0;
        switch (severity.toUpperCase()) {
            case "CRITICAL": return 5.8;
            case "HIGH":     return 3.5;
            case "MEDIUM":   return 2.0;
            case "LOW":      return 1.0;
            default:         return 1.0;
        }
    }

    private double clamp(double score) {
        return Math.max(1.0, Math.min(10.0, score));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }


    // =====================================================
    // GET ALL REVIEWS
    // =====================================================

    public List<Map<String, Object>> getAllReviews() {

        return reviewRepository.getAllReviews();
    }


    // =====================================================
    // GET REVIEW BY ID
    // =====================================================

    public Map<String, Object> getReviewById(
            long id) {

        return reviewRepository.getReviewById(
                id
        );
    }
}