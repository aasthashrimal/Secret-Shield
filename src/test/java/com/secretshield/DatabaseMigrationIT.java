package com.secretshield;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import com.secretshield.config.DatabaseMigrationConfig;
import com.secretshield.support.PostgresApiTest;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseMigrationIT extends PostgresApiTest {
    @Autowired DataSource datasource;

    record TestSchema(Connection connection, Flyway flyway) {}
    interface SchemaCheck { void run(TestSchema schema) throws Exception; }

    private void inDisposableSchema(SchemaCheck check) throws Exception {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        // The datasource is already verified by PostgresApiTest; the identifier is generated here.
        try (Connection connection = datasource.getConnection(); var statement = connection.createStatement()) {
            String previous = connection.getSchema();
            statement.execute("CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                Flyway flyway = Flyway.configure().dataSource(datasource).defaultSchema(schema).schemas(schema)
                        .locations("classpath:db/migration").baselineVersion("0").baselineOnMigrate(false)
                        .cleanDisabled(true).load();
                check.run(new TestSchema(connection, flyway));
            } finally {
                connection.setSchema(previous);
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private void legacyUser(Connection connection, boolean updateColumn) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(new ClassPathResource("fixtures/legacy-users.sql")
                    .getContentAsString(StandardCharsets.UTF_8));
            if (updateColumn) { statement.execute("ALTER TABLE users ADD COLUMN updated_at timestamp(6)"); }
        }
        try (var insert = connection.prepareStatement(
                "INSERT INTO users(username,email,password,role,created_at) VALUES (?,?,?,?,?)")) {
            insert.setString(1, "synthetic-existing-user");
            insert.setString(2, "existing@example.invalid");
            insert.setString(3, "synthetic-existing-password-hash");
            insert.setString(4, "DEVELOPER");
            insert.setTimestamp(5, Timestamp.valueOf(LocalDateTime.of(2025, 1, 2, 3, 4, 5)));
            insert.executeUpdate();
        }
    }

    private void assertPreserved(Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT username,email,password,role,created_at,updated_at FROM users")) {
            assertTrue(rows.next());
            assertEquals("synthetic-existing-user", rows.getString(1));
            assertEquals("existing@example.invalid", rows.getString(2));
            assertTrue("synthetic-existing-password-hash".equals(rows.getString(3)), "Existing hash must remain unchanged");
            assertEquals("DEVELOPER", rows.getString(4));
            assertEquals(LocalDateTime.of(2025, 1, 2, 3, 4, 5), rows.getTimestamp(5).toLocalDateTime());
            assertEquals(rows.getTimestamp(5), rows.getTimestamp(6));
            assertFalse(rows.next());
        }
    }

    @Test void freshDatabaseMigratesAndRepeatStartupIsIdempotent() throws Exception {
        inDisposableSchema(schema -> {
            DatabaseMigrationConfig.migrate(schema.flyway(), false);
            DatabaseMigrationConfig.migrate(schema.flyway(), false);
            assertEquals("8", schema.flyway().info().current().getVersion().getVersion());
            assertEquals(8, schema.flyway().info().applied().length);
            try (var statement = schema.connection().createStatement(); var rows = statement.executeQuery(
                    "SELECT is_nullable FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='users' AND column_name='updated_at'")) {
                assertTrue(rows.next());
                assertEquals("NO", rows.getString(1));
            }
            try (var statement = schema.connection().createStatement(); var rows = statement.executeQuery(
                    "SELECT delete_rule FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=current_schema() AND constraint_name='project_repositories_owner_fk'")) {
                assertTrue(rows.next());
                assertEquals("RESTRICT", rows.getString(1));
            }
        });
    }

    @Test void v8BackfillsHistoricalFindingsIntoOneRepositoryIncident() throws Exception {
        inDisposableSchema(schema -> {
            String name = schema.connection().getSchema();
            Flyway throughV7 = Flyway.configure().dataSource(datasource)
                    .defaultSchema(name).schemas(name).locations("classpath:db/migration")
                    .target("7").cleanDisabled(true).load();
            DatabaseMigrationConfig.migrate(throughV7, false);
            try (var statement = schema.connection().createStatement()) {
                statement.execute("""
                    INSERT INTO users (id,username,email,password,role,created_at,updated_at)
                    VALUES (7001,'legacy-finding','legacy-finding@example.invalid','hash','DEVELOPER',
                            TIMESTAMP '2026-01-01 00:00:00',TIMESTAMP '2026-01-01 00:00:00')
                    """);
                statement.execute("""
                    INSERT INTO project_repositories (id,owner_id,github_owner,github_name,
                        canonical_url,default_branch,active,created_at,updated_at)
                    VALUES (7002,7001,'demo','legacy','https://github.com/demo/legacy','main',TRUE,
                            TIMESTAMP '2026-01-01 00:00:00',TIMESTAMP '2026-01-01 00:00:00')
                    """);
                statement.execute("""
                    INSERT INTO scans (id,repository_id,commit_sha,branch,trigger,run_id,run_attempt,
                        scanner_version,status,created_at,updated_at)
                    VALUES (7003,7002,repeat('0',40),'main','CI_PUSH','legacy-run',1,'8.30.0',
                            'FINDINGS_DETECTED',TIMESTAMP '2026-01-01 00:00:00',TIMESTAMP '2026-01-01 00:00:00'),
                           (7004,7002,repeat('0',40),'main','CI_PUSH','legacy-run',2,'8.30.0',
                            'FINDINGS_DETECTED',TIMESTAMP '2026-01-02 00:00:00',TIMESTAMP '2026-01-02 00:00:00')
                    """);
                statement.execute("""
                    INSERT INTO findings (id,scan_id,fingerprint,rule_id,severity,file_path,line,
                        commit_sha,sanitized_description,created_at)
                    VALUES (7005,7003,repeat('a',64),'synthetic-low','LOW','config/demo.txt',2,
                            repeat('0',40),'Potential secret detected',TIMESTAMP '2026-01-01 00:00:00'),
                           (7006,7004,repeat('a',64),'synthetic-low','HIGH','config/demo.txt',2,
                            repeat('0',40),'Potential secret detected',TIMESTAMP '2026-01-02 00:00:00')
                    """);
            }
            DatabaseMigrationConfig.migrate(schema.flyway(), false);
            try (var statement = schema.connection().createStatement();
                    var rows = statement.executeQuery("""
                        SELECT i.repository_id,i.severity,i.status,count(f.id),count(DISTINCT a.id)
                        FROM incidents i JOIN findings f ON f.incident_id=i.id
                        JOIN audit_events a ON a.target_type='INCIDENT' AND a.target_id=i.id
                        GROUP BY i.id
                        """)) {
                assertTrue(rows.next());
                assertEquals(7002, rows.getLong(1));
                assertEquals("HIGH", rows.getString(2));
                assertEquals("OPEN", rows.getString(3));
                assertEquals(2, rows.getInt(4));
                assertEquals(1, rows.getInt(5));
                assertFalse(rows.next());
            }
        });
    }

    @Test void explicitAdoptionPreservesUsersAndAddsUpdateTimestamp() throws Exception {
        inDisposableSchema(schema -> {
            legacyUser(schema.connection(), false);
            DatabaseMigrationConfig.migrate(schema.flyway(), true);
            assertPreserved(schema.connection());
            DatabaseMigrationConfig.migrate(schema.flyway(), false);
            assertPreserved(schema.connection());
        });
    }

    @Test void existingNullableUpdateColumnIsBackfilled() throws Exception {
        inDisposableSchema(schema -> {
            legacyUser(schema.connection(), true);
            DatabaseMigrationConfig.migrate(schema.flyway(), true);
            assertPreserved(schema.connection());
        });
    }

    @Test void existingSchemaIsNotAutomaticallyBaselined() throws Exception {
        inDisposableSchema(schema -> {
            legacyUser(schema.connection(), false);
            assertThrows(IllegalStateException.class, () -> DatabaseMigrationConfig.migrate(schema.flyway(), false));
            try (var statement = schema.connection().createStatement(); var rows = statement.executeQuery(
                    "SELECT to_regclass(current_schema() || '.flyway_schema_history') IS NULL")) {
                assertTrue(rows.next());
                assertTrue(rows.getBoolean(1));
            }
        });
    }

    @Test void incompatibleLegacySchemaIsRejectedBeforeBaseline() throws Exception {
        inDisposableSchema(schema -> {
            legacyUser(schema.connection(), false);
            try (var statement = schema.connection().createStatement()) {
                statement.execute("ALTER TABLE users RENAME COLUMN password TO unsupported_password");
            }
            assertThrows(IllegalStateException.class, () -> DatabaseMigrationConfig.migrate(schema.flyway(), true));
            try (var statement = schema.connection().createStatement(); var rows = statement.executeQuery(
                    "SELECT to_regclass(current_schema() || '.flyway_schema_history') IS NULL")) {
                assertTrue(rows.next());
                assertTrue(rows.getBoolean(1));
            }
        });
    }

    @Test void normalizationPreservesExistingIdentityAndHash() throws Exception {
        inDisposableSchema(schema -> {
            legacyUser(schema.connection(), false);
            try (var statement = schema.connection().createStatement()) {
                statement.execute("UPDATE users SET email=' EXISTING@EXAMPLE.INVALID '");
            }
            DatabaseMigrationConfig.migrate(schema.flyway(), true);
            assertPreserved(schema.connection());
        });
    }

    @Test void normalizationCollisionFailsWithoutMergingOrChangingUsers() throws Exception {
        inDisposableSchema(schema -> {
            legacyUser(schema.connection(), false);
            try (var statement = schema.connection().createStatement()) {
                statement.execute("INSERT INTO users(username,email,password,role,created_at) "
                        + "VALUES ('synthetic-collision','EXISTING@EXAMPLE.INVALID','synthetic-hash','DEVELOPER',TIMESTAMP '2025-01-02 03:04:05')");
            }
            assertThrows(org.flywaydb.core.api.FlywayException.class,
                    () -> DatabaseMigrationConfig.migrate(schema.flyway(), true));
            try (var statement = schema.connection().createStatement(); var rows = statement.executeQuery(
                    "SELECT count(*), count(*) FILTER (WHERE email='EXISTING@EXAMPLE.INVALID') FROM users")) {
                assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); assertEquals(1, rows.getInt(2));
            }
            assertEquals("2", schema.flyway().info().current().getVersion().getVersion());
        });
    }

    @Test void roleMigrationNormalizesSupportedHistoricalRole() throws Exception {
        inDisposableSchema(schema -> {
            legacyUser(schema.connection(), false);
            try (var statement = schema.connection().createStatement()) {
                statement.execute("UPDATE users SET role=' developer '");
            }
            DatabaseMigrationConfig.migrate(schema.flyway(), true);
            try (var statement = schema.connection().createStatement();
                    var rows = statement.executeQuery("SELECT role FROM users")) {
                assertTrue(rows.next());
                assertEquals("DEVELOPER", rows.getString(1));
            }
        });
    }

    @Test void unsupportedHistoricalRoleStopsMigrationWithoutChangingIt() throws Exception {
        inDisposableSchema(schema -> {
            legacyUser(schema.connection(), false);
            DatabaseMigrationConfig.migrate(schema.flyway(), true);
            try (var statement = schema.connection().createStatement()) {
                statement.execute("ALTER TABLE users DROP CONSTRAINT users_role_allowed");
                statement.execute("UPDATE users SET role='SUPERUSER'");
                statement.execute("DELETE FROM flyway_schema_history WHERE version IN ('4','5','6','7','8')");
            }
            assertThrows(org.flywaydb.core.api.FlywayException.class,
                    () -> DatabaseMigrationConfig.migrate(schema.flyway(), false));
            try (var statement = schema.connection().createStatement();
                    var rows = statement.executeQuery("SELECT role FROM users")) {
                assertTrue(rows.next());
                assertEquals("SUPERUSER", rows.getString(1));
            }
            assertEquals("3", schema.flyway().info().current().getVersion().getVersion());
        });
    }
}
