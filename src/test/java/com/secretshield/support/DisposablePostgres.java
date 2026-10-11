package com.secretshield.support;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Pattern;

/** Fail closed before Hibernate can connect or create tables. Never echo input values. */
final class DisposablePostgres {
    private static final Pattern RUN_ID = Pattern.compile("[a-f0-9]{32}");
    private static final Pattern URL = Pattern.compile(
            "jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{1,5})/secret_shield_test_([a-f0-9]{32})");

    record Settings(String url, String username, String password, String runId) {}

    static Settings settings(Map<String, String> environment) {
        String runId = environment.getOrDefault("TEST_DB_RUN_ID", "");
        String url = environment.getOrDefault("TEST_DB_URL", "");
        String username = environment.getOrDefault("TEST_DB_USERNAME", "");
        String password = environment.getOrDefault("TEST_DB_PASSWORD", "");
        var match = URL.matcher(url);
        if (!RUN_ID.matcher(runId).matches() || !match.matches()
                || !match.group(2).equals(runId) || !username.equals("secretshield_test")
                || password.isBlank()) {
            throw new IllegalStateException("Isolated PostgreSQL settings missing or invalid; use scripts/test-postgres.ps1");
        }
        int port = Integer.parseInt(match.group(1));
        if (port < 1 || port > 65535 || port == 5432) {
            throw new IllegalStateException("Disposable PostgreSQL requires a dedicated non-default port");
        }
        return new Settings(url, username, password, runId);
    }

    static void verify(Settings settings) {
        // Timeout and errors are generic so neither JDBC URLs nor credentials enter test output.
        var properties = new java.util.Properties();
        properties.setProperty("user", settings.username());
        properties.setProperty("password", settings.password());
        properties.setProperty("connectTimeout", "5");
        try (var connection = DriverManager.getConnection(settings.url(), properties);
                var statement = connection.prepareStatement(
                        "select current_database(), current_user, run_id from test_support.test_run_marker")) {
            statement.setQueryTimeout(5);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || !rows.getString(1).equals("secret_shield_test_" + settings.runId())
                        || !rows.getString(2).equals(settings.username())
                        || !rows.getString(3).equals(settings.runId()) || rows.next()) {
                    throw new IllegalStateException("Database isolation marker does not match this test run");
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot verify disposable database marker; use scripts/test-postgres.ps1");
        }
    }

    private DisposablePostgres() {}
}
