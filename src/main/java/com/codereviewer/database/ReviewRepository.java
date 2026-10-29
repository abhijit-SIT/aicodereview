package com.codereviewer.database;

import com.codereviewer.model.ReviewResult;
import com.codereviewer.model.codeIssue;

import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReviewRepository {

    // =====================================================
    // SAVE REVIEW
    // =====================================================

    
public long saveReview(
        ReviewResult result,
        String filePath,
        String aiReview) {

    String sql = """
            INSERT INTO reviews
                (file_name, file_path, review_date,
                 total_issues, ai_review)
            VALUES (?, ?, ?, ?, ?)
            """;

    try (Connection connection = DatabaseManager.getConnection()) {

        connection.setAutoCommit(false);

        try {
            long reviewId;

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 sql,
                                 Statement.RETURN_GENERATED_KEYS)) {

                statement.setString(1, result.getFilename());
                statement.setString(2, filePath);
                statement.setString(3, result.getReviewDate());
                statement.setInt(4, result.getIssues().size());
                statement.setString(5, aiReview);

                statement.executeUpdate();

                try (ResultSet keys = statement.getGeneratedKeys()) {

                    if (!keys.next()) {
                        throw new SQLException(
                                "PostgreSQL did not return the new review ID."
                        );
                    }

                    reviewId = keys.getLong(1);
                }
            }

            saveIssues(connection, reviewId, result.getIssues());

            connection.commit();

            System.out.println(
                    "Review saved to PostgreSQL. ID: " + reviewId
            );

            return reviewId;

        } catch (Exception e) {

            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }

            throw e;
        }

    } catch (Exception e) {

        System.err.println("Failed to save review to PostgreSQL.");
        e.printStackTrace();

        return -1;
    }
}



    // =====================================================
    // SAVE ISSUES
    // =====================================================

    private void saveIssues(
            Connection connection,
            long reviewId,
            List<codeIssue> issues)
            throws SQLException {

        if (issues == null ||
                issues.isEmpty()) {

            return;
        }

        String sql = """
                INSERT INTO issues
                (
                    review_id,
                    rule,
                    severity,
                    line,
                    message,
                    suggestion
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            for (codeIssue issue : issues) {

                statement.setLong(
                        1,
                        reviewId
                );

                statement.setString(
                        2,
                        issue.getRule()
                );

                statement.setString(
                        3,
                        issue.getSeverity()
                );

                statement.setInt(
                        4,
                        issue.getLine()
                );

                statement.setString(
                        5,
                        issue.getMessage()
                );

                statement.setString(
                        6,
                        issue.getSuggestion()
                );

                statement.addBatch();
            }

            statement.executeBatch();
        }
    }


    // =====================================================
    // GET ALL REVIEWS
    // =====================================================

    public List<Map<String, Object>> getAllReviews() {

        String sql = """
                SELECT
                    id,
                    file_name,
                    file_path,
                    review_date,
                    total_issues,
                    ai_review
                FROM reviews
                ORDER BY id DESC
                """;

        List<Map<String, Object>> reviews =
                new ArrayList<>();

        try (Connection connection =
                     DatabaseManager.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql);

             ResultSet resultSet =
                     statement.executeQuery()) {

            while (resultSet.next()) {

                Map<String, Object> review =
                        new HashMap<>();

                review.put(
                        "id",
                        resultSet.getLong("id")
                );

                review.put(
                        "fileName",
                        resultSet.getString("file_name")
                );

                review.put(
                        "filePath",
                        resultSet.getString("file_path")
                );

                review.put(
                        "reviewDate",
                        resultSet.getString("review_date")
                );

                review.put(
                        "totalIssues",
                        resultSet.getInt("total_issues")
                );

                review.put(
                        "aiReview",
                        resultSet.getString("ai_review")
                );

                reviews.add(review);
            }

        } catch (SQLException e) {

            System.out.println(
                    "Failed to retrieve reviews."
            );

            e.printStackTrace();
        }

        return reviews;
    }


    // =====================================================
    // GET REVIEW BY ID
    // =====================================================

    public Map<String, Object> getReviewById(long id) {

        String sql = """
                SELECT
                    id,
                    file_name,
                    file_path,
                    review_date,
                    total_issues,
                    ai_review
                FROM reviews
                WHERE id = ?
                """;

        try (Connection connection =
                     DatabaseManager.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            System.out.println(
                    "Searching database for review ID: "
                            + id
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {

                    Map<String, Object> review =
                            new HashMap<>();

                    review.put(
                            "id",
                            resultSet.getLong("id")
                    );

                    review.put(
                            "fileName",
                            resultSet.getString("file_name")
                    );

                    review.put(
                            "filePath",
                            resultSet.getString("file_path")
                    );

                    review.put(
                            "reviewDate",
                            resultSet.getString("review_date")
                    );

                    review.put(
                            "totalIssues",
                            resultSet.getInt("total_issues")
                    );

                    review.put(
                            "aiReview",
                            resultSet.getString("ai_review")
                    );

                    // Add issues
                    review.put(
                            "issues",
                            getIssuesByReviewId(id)
                    );

                    System.out.println(
                            "Review found: ID " + id
                    );

                    return review;
                }

                System.out.println(
                        "No review found with ID: " + id
                );
            }

        } catch (SQLException e) {

            System.out.println(
                    "Failed to retrieve review ID: "
                            + id
            );

            e.printStackTrace();
        }

        return null;
    }


    // =====================================================
    // GET ISSUES FOR REVIEW
    // =====================================================

    private List<Map<String, Object>> getIssuesByReviewId(
            long reviewId) {

        String sql = """
                SELECT
                    id,
                    rule,
                    severity,
                    line,
                    message,
                    suggestion
                FROM issues
                WHERE review_id = ?
                ORDER BY id
                """;

        List<Map<String, Object>> issues =
                new ArrayList<>();

        try (Connection connection =
                     DatabaseManager.getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    reviewId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {

                    Map<String, Object> issue =
                            new HashMap<>();

                    issue.put(
                            "id",
                            resultSet.getLong("id")
                    );

                    issue.put(
                            "rule",
                            resultSet.getString("rule")
                    );

                    issue.put(
                            "severity",
                            resultSet.getString("severity")
                    );

                    issue.put(
                            "line",
                            resultSet.getInt("line")
                    );

                    issue.put(
                            "message",
                            resultSet.getString("message")
                    );

                    issue.put(
                            "suggestion",
                            resultSet.getString("suggestion")
                    );

                    issues.add(issue);
                }
            }

        } catch (SQLException e) {

            System.out.println(
                    "Failed to retrieve issues."
            );

            e.printStackTrace();
        }

        return issues;
    }
}