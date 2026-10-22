package com.codereviewer.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReviewResult {

    private final String filename;

    private final List<codeIssue> issues =
            new ArrayList<>();

    private String aiReview;

    private String filePath;

    private String reviewDate;

    // ── Computed scores (populated by ReviewService) ──
    private double secScore;
    private double qualScore;
    private double maintScore;
    private double overallScore;
    private String verdict;

    public ReviewResult(String filename) {
        this.filename = filename;
        this.reviewDate = LocalDateTime.now().toString();
    }

    // =========================================================
    // ADD ISSUE
    // =========================================================

    public void addIssue(codeIssue issue) {
        issues.add(issue);
    }

    // =========================================================
    // GET FILENAME
    // =========================================================

    public String getFilename() {
        return filename;
    }

    // =========================================================
    // GET ISSUES
    // =========================================================

    public List<codeIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    // =========================================================
    // AI REVIEW
    // =========================================================

    public void setAiReview(String aiReview) {
        this.aiReview = aiReview;
    }

    public String getAiReview() {
        return aiReview;
    }

    // =========================================================
    // FILE PATH
    // =========================================================

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    // =========================================================
    // REVIEW DATE
    // =========================================================

    public String getReviewDate() {
        return reviewDate;
    }

    public void setReviewDate(String reviewDate) {
        this.reviewDate = reviewDate;
    }

    // =========================================================
    // SCORES
    // =========================================================

    public double getSecScore() {
        return secScore;
    }

    public void setSecScore(double secScore) {
        this.secScore = secScore;
    }

    public double getQualScore() {
        return qualScore;
    }

    public void setQualScore(double qualScore) {
        this.qualScore = qualScore;
    }

    public double getMaintScore() {
        return maintScore;
    }

    public void setMaintScore(double maintScore) {
        this.maintScore = maintScore;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }
}