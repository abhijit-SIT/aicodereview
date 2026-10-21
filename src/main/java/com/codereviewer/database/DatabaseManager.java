
package com.codereviewer.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseManager {

    private static final String DEFAULT_URL =
            "jdbc:postgresql://localhost:5432/ai_code_reviewer";

    private static final String URL =
            getEnvironmentVariable("DB_URL", DEFAULT_URL);

    private static final String USERNAME =
            getEnvironmentVariable("DB_USERNAME", "reviewer_app");

    private static final String PASSWORD =
            getEnvironmentVariable("DB_PASSWORD", null);

    private DatabaseManager() {
        // Utility class; do not instantiate.
    }

    public static Connection getConnection() throws SQLException {
        if (PASSWORD == null || PASSWORD.isBlank()) {
            throw new SQLException(
                    "DB_PASSWORD is not configured. "
                    + "Set the PostgreSQL password environment variable.");
        }

        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    private static String getEnvironmentVariable(
            String name, String defaultValue) {

        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }
}
