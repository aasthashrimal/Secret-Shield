package com.secretshield.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                // Verify checked-in configuration independently of private shell variables.
                context.getEnvironment().getPropertySources()
                        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                new ConfigDataApplicationContextInitializer().initialize(context);
            });

    @Test void localDefaultUsesMigrationBackedSchemaValidation() {
        runner.withPropertyValues("DB_URL=jdbc:postgresql://localhost:5432/synthetic_local",
                "DB_USERNAME=synthetic_local_user").run(context -> {
            var env = context.getEnvironment();
            assertThat(env.getDefaultProfiles()).containsExactly("local");
            assertThat(env.getProperty("spring.datasource.url")).endsWith("/synthetic_local");
            assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(env.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
            assertThat(env.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone")).isEqualTo("UTC");
        });
    }

    @Test void testProfileUsesDedicatedInputsAndDoesNotChangeSchema() {
        runner.withPropertyValues("spring.profiles.active=test",
                "DB_URL=jdbc:postgresql://localhost:5432/synthetic_development",
                "DB_USERNAME=synthetic_development_user", "DB_PASSWORD=synthetic-development-password",
                "JWT_SECRET=synthetic-development-key",
                "TEST_DB_URL=jdbc:postgresql://localhost:5432/synthetic_test",
                "TEST_DB_USERNAME=synthetic_test_user", "TEST_DB_PASSWORD=synthetic-test-password")
                .run(context -> {
                    var env = context.getEnvironment();
                    assertThat(env.getProperty("spring.datasource.url")).endsWith("/synthetic_test");
                    assertThat(env.getProperty("spring.datasource.username")).isEqualTo("synthetic_test_user");
                    assertThat(env.getProperty("spring.datasource.password")).isEqualTo("synthetic-test-password");
                    assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
                    assertThat(env.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
                    assertThat(env.getProperty("jwt.secret").startsWith("synthetic-test-only-"))
                            .as("test profile supplies a synthetic signing key").isTrue();
                });
    }

    @Test void otherProfilesDoNotInheritLocalSchemaUpdates() {
        runner.withPropertyValues("spring.profiles.active=deployment").run(context -> {
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(context.getEnvironment().getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        });
    }

    @Test void testProfileNeverFallsBackToDevelopmentDatabaseVariables() {
        runner.withPropertyValues("spring.profiles.active=test",
                "DB_URL=jdbc:postgresql://localhost:5432/synthetic_development",
                "DB_USERNAME=synthetic_development_user", "DB_PASSWORD=synthetic-development-password")
                .run(context -> {
                    var env = context.getEnvironment();
                    assertThrows(IllegalArgumentException.class, () -> env.getProperty("spring.datasource.url"));
                    assertThrows(IllegalArgumentException.class, () -> env.getProperty("spring.datasource.username"));
                    assertThrows(IllegalArgumentException.class, () -> env.getProperty("spring.datasource.password"));
                });
    }
}
