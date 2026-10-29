package com.codereviewer.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReviewResult {

    private final String filename;
    private final List<codeIssue> issues = new ArrayList<>();
    private String aiReview;
    private String filePath;
    private String reviewDate;

    public ReviewResult(String filename) {
        this.filename = filename;
        this.reviewDate = LocalDateTime.now().toString();
    }

    public void addIssue(codeIssue issue) {
        if (issue != null) {
            issues.add(issue);
        }
    }

    public String getFilename() {
        return filename;
    }

    /**
     * Returns a read-only view that Jackson can serialize as the "issues" JSON array.
     */
    public List<codeIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public void setAiReview(String aiReview) {
        this.aiReview = aiReview;
    }

    /**
     * Jackson serializes this getter as the "aiReview" JSON property.
     */
    public String getAiReview() {
        return aiReview;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getReviewDate() {
        return reviewDate;
    }

    public void setReviewDate(String reviewDate) {
        this.reviewDate = reviewDate;
    }
}
