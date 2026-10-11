package com.secretshield.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DatabaseMigrationConfig {
    @Bean
    FlywayMigrationStrategy migrationStrategy(
            @Value("${secretshield.database.adopt-existing:false}") boolean adoptExisting) {
        return flyway -> migrate(flyway, adoptExisting);
    }

    /** Public to allow the same adoption procedure to be exercised in isolated tests. */
    public static void migrate(Flyway flyway, boolean adoptExisting) {
        String schema = flyway.getConfiguration().getDefaultSchema();
        if (schema == null) { schema = "public"; }
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            Set<String> tables = new HashSet<>();
            try (var rows = connection.getMetaData().getTables(null, schema, "%", new String[]{"TABLE"})) {
                while (rows.next()) { tables.add(rows.getString("TABLE_NAME")); }
            }
            if (!tables.contains("flyway_schema_history") && !tables.isEmpty()) {
                if (!adoptExisting) {
                    throw new IllegalStateException("Existing schema needs explicit adoption; see docs/database-migrations.md");
                }
                if (!tables.equals(Set.of("users"))) {
                    throw new IllegalStateException("Existing schema contains unexpected tables; adoption refused");
                }
                verifyUsers(connection, schema);
                flyway.baseline();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Database adoption check failed; no automatic baseline was performed");
        }
        flyway.migrate();
    }

    private static void verifyUsers(Connection connection, String schema) throws SQLException {
        Map<String, Integer> required = Map.of("id", Types.BIGINT, "username", Types.VARCHAR,
                "email", Types.VARCHAR, "password", Types.VARCHAR, "role", Types.VARCHAR,
                "created_at", Types.TIMESTAMP);
        Set<String> columns = new HashSet<>();
        try (var rows = connection.getMetaData().getColumns(null, schema, "users", "%")) {
            while (rows.next()) {
                String name = rows.getString("COLUMN_NAME");
                columns.add(name);
                Integer type = required.get(name);
                if (type == null && !name.equals("updated_at")) { refuse(); }
                if (rows.getInt("DATA_TYPE") != (type == null ? Types.TIMESTAMP : type)) { refuse(); }
                if (required.containsKey(name) && rows.getInt("NULLABLE") != 0) { refuse(); }
                if (type != null && type == Types.VARCHAR && rows.getInt("COLUMN_SIZE") != 255) { refuse(); }
                if (name.equals("id") && !"YES".equals(rows.getString("IS_AUTOINCREMENT"))) { refuse(); }
            }
        }
        if (!columns.containsAll(required.keySet())) { refuse(); }
        Set<String> keys = new HashSet<>();
        try (var rows = connection.getMetaData().getPrimaryKeys(null, schema, "users")) {
            while (rows.next()) { keys.add(rows.getString("COLUMN_NAME")); }
        }
        if (!keys.equals(Set.of("id"))) { refuse(); }
        Map<String, Set<String>> uniqueIndexes = new HashMap<>();
        try (var rows = connection.getMetaData().getIndexInfo(null, schema, "users", true, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME");
                String column = rows.getString("COLUMN_NAME");
                if (name != null && column != null && rows.getString("FILTER_CONDITION") == null) {
                    uniqueIndexes.computeIfAbsent(name, key -> new HashSet<>()).add(column);
                }
            }
        }
        if (!uniqueIndexes.containsValue(Set.of("username")) || !uniqueIndexes.containsValue(Set.of("email"))) { refuse(); }
    }

    private static void refuse() {
        throw new IllegalStateException("Existing users schema differs from the supported baseline; adoption refused");
    }
}
