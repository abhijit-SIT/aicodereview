
package com.codereviewer.database;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteToPostgresMigration {

    private static final String SQLITE_DEFAULT =
            "C:/Users/HP/desktop/aicodereview/reviews.db";

    private static final String POSTGRES_URL =
            "jdbc:postgresql://localhost:5432/ai_code_reviewer";

    private SqliteToPostgresMigration() {
    }

    public static void main(String[] args) throws Exception {
        String sqlitePath = env("SQLITE_DB_PATH", SQLITE_DEFAULT);
        String postgresUrl = env("DB_URL", POSTGRES_URL);
        String postgresUser = env("DB_USERNAME", "reviewer_app");
        String postgresPassword = env("DB_PASSWORD", null);

        if (postgresPassword == null || postgresPassword.isBlank()) {
            throw new IllegalStateException(
                    "Set the DB_PASSWORD environment variable first.");
        }

        Path source = Path.of(sqlitePath).toAbsolutePath().normalize();

        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException(
                    "SQLite database file not found: " + source);
        }

        System.out.println("SQLite source: " + source);
        System.out.println("PostgreSQL destination: " + postgresUrl);

        try (Connection sqlite = DriverManager.getConnection(
                     "jdbc:sqlite:" + source);
             Connection postgres = DriverManager.getConnection(
                     postgresUrl, postgresUser, postgresPassword)) {

            verifySqliteTables(sqlite);

            long sourceReviews = count(sqlite, "reviews");
            long sourceIssues = count(sqlite, "issues");

            System.out.println("SQLite reviews: " + sourceReviews);
            System.out.println("SQLite issues: " + sourceIssues);

            long existingReviews = count(postgres, "public.reviews");
            long existingIssues = count(postgres, "public.issues");

            if (existingReviews != 0 || existingIssues != 0) {
                throw new IllegalStateException(
                        "PostgreSQL target is not empty. Found reviews="
                        + existingReviews + ", issues=" + existingIssues
                        + ". Migration stopped to prevent duplicates.");
            }

            postgres.setAutoCommit(false);

            try {
                long copiedReviews = copyReviews(sqlite, postgres);
                long copiedIssues = copyIssues(sqlite, postgres);

                if (copiedReviews != sourceReviews
                        || copiedIssues != sourceIssues) {
                    throw new SQLException(
                            "Source and copied row counts do not match.");
                }

                resetIdentitySequence(postgres, "reviews");
                resetIdentitySequence(postgres, "issues");

                postgres.commit();

                System.out.println("Migration completed successfully.");
                System.out.println("Reviews copied: " + copiedReviews);
                System.out.println("Issues copied: " + copiedIssues);

            } catch (Exception e) {
                try {
                    postgres.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            } finally {
                postgres.setAutoCommit(true);
            }
        }
    }

    private static void verifySqliteTables(Connection connection)
            throws SQLException {

        try (Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT id FROM reviews LIMIT 0");
            statement.executeQuery("SELECT id FROM issues LIMIT 0");
        }
    }

    private static long count(Connection connection, String table)
            throws SQLException {

        // Table names are fixed constants supplied by this class.
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long copyReviews(
            Connection sqlite, Connection postgres) throws SQLException {

        String selectSql = """
                SELECT id, file_name, file_path, review_date,
                       total_issues, ai_review
                FROM reviews
                ORDER BY id
                """;

        String insertSql = """
                INSERT INTO public.reviews
                    (id, file_name, file_path, review_date,
                     total_issues, ai_review)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        long copied = 0;

        try (Statement read = sqlite.createStatement();
             ResultSet rows = read.executeQuery(selectSql);
             PreparedStatement insert = postgres.prepareStatement(insertSql)) {

            while (rows.next()) {
                insert.setLong(1, rows.getLong("id"));
                insert.setString(2, rows.getString("file_name"));
                insert.setString(3, rows.getString("file_path"));
                insert.setString(4, rows.getString("review_date"));

                int totalIssues = rows.getInt("total_issues");
                if (rows.wasNull()) {
                    insert.setNull(5, java.sql.Types.INTEGER);
                } else {
                    insert.setInt(5, totalIssues);
                }

                insert.setString(6, rows.getString("ai_review"));
                insert.executeUpdate();
                copied++;
            }
        }

        return copied;
    }

    private static long copyIssues(
            Connection sqlite, Connection postgres) throws SQLException {

        String selectSql = """
                SELECT id, review_id, rule, severity,
                       line, message, suggestion
                FROM issues
                ORDER BY id
                """;

        String insertSql = """
                INSERT INTO public.issues
                    (id, review_id, rule, severity,
                     line, message, suggestion)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        long copied = 0;

        try (Statement read = sqlite.createStatement();
             ResultSet rows = read.executeQuery(selectSql);
             PreparedStatement insert = postgres.prepareStatement(insertSql)) {

            while (rows.next()) {
                insert.setLong(1, rows.getLong("id"));
                insert.setLong(2, rows.getLong("review_id"));
                insert.setString(3, rows.getString("rule"));
                insert.setString(4, rows.getString("severity"));

                int line = rows.getInt("line");
                if (rows.wasNull()) {
                    insert.setNull(5, java.sql.Types.INTEGER);
                } else {
                    insert.setInt(5, line);
                }

                insert.setString(6, rows.getString("message"));
                insert.setString(7, rows.getString("suggestion"));
                insert.executeUpdate();
                copied++;
            }
        }

        return copied;
    }

    private static void resetIdentitySequence(
            Connection connection, String table) throws SQLException {

        String sql = """
                SELECT setval(
                    pg_get_serial_sequence(?, 'id'),
                    COALESCE(MAX(id), 1),
                    COUNT(*) > 0
                )
                FROM public.%s
                """.formatted(table);

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            statement.setString(1, "public." + table);
            statement.executeQuery().close();
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank()
                ? defaultValue : value;
    }
}
