package com.secretshield.support;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisposablePostgresTests {
    private Map<String, String> valid() {
        return new HashMap<>(Map.of("TEST_DB_RUN_ID", "a".repeat(32),
                "TEST_DB_URL", "jdbc:postgresql://127.0.0.1:15432/secret_shield_test_" + "a".repeat(32),
                "TEST_DB_USERNAME", "secretshield_test", "TEST_DB_PASSWORD", "synthetic-test-password"));
    }

    @Test void acceptsDedicatedRunnerSettings() {
        assertDoesNotThrow(() -> DisposablePostgres.settings(valid()));
    }

    @Test void rejectsDevelopmentDatabaseAndDefaultPort() {
        var env = valid();
        env.put("TEST_DB_URL", "jdbc:postgresql://localhost:5432/secret_shield_db");
        assertThrows(IllegalStateException.class, () -> DisposablePostgres.settings(env));
        env.put("TEST_DB_URL", "jdbc:postgresql://127.0.0.1:5432/secret_shield_test_" + "a".repeat(32));
        assertThrows(IllegalStateException.class, () -> DisposablePostgres.settings(env));
    }

    @Test void rejectsMissingSettingsEvenWhenDevelopmentVariablesExist() {
        assertThrows(IllegalStateException.class, () -> DisposablePostgres.settings(Map.of(
                "DB_URL", "jdbc:postgresql://localhost:5432/secret_shield_db", "DB_PASSWORD", "synthetic")));
    }

    @Test void rejectsRunMismatchCredentialsAndJdbcOptions() {
        for (String name : new String[]{"TEST_DB_RUN_ID", "TEST_DB_USERNAME", "TEST_DB_PASSWORD", "TEST_DB_URL"}) {
            var env = valid();
            env.put(name, name.equals("TEST_DB_URL") ? env.get(name) + "?currentSchema=public" : "");
            assertThrows(IllegalStateException.class, () -> DisposablePostgres.settings(env));
        }
    }
}
