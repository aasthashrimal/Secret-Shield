package com.secretshield;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;
import com.secretshield.repository.UserRepository;
import com.secretshield.support.PostgresApiTest;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiFoundationIT extends PostgresApiTest {
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;

    private Map<String, String> registration() {
        return Map.of("username", "synthetic-tester", "email", "test@example.invalid",
                "password", "synthetic-test-password");
    }

    @BeforeEach void previousTestWritesWereRolledBack() {
        assertEquals(0, users.count(), "Integration tests must not inherit user rows");
    }

    @Test void databaseIdentityIsDisposablePostgres() {
        assertTrue(jdbc.queryForObject("select current_database()", String.class)
                .startsWith("secret_shield_test_"));
        assertEquals("secretshield_test", jdbc.queryForObject("select current_user", String.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from test_support.test_run_marker", Integer.class));
    }

    @Test void registrationPersistsSafeUserWithAutomaticUtcTimestamps() throws Exception {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        postJson("/api/auth/register", registration()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
        entities.flush();
        entities.clear();
        User stored = users.findByEmail("test@example.invalid").orElseThrow();
        assertTrue(passwords.matches("synthetic-test-password", stored.getPassword()), "Stored password must be BCrypt");
        assertTrue(stored.getCreatedAt().isAfter(before));
        assertTrue(stored.getCreatedAt().isBefore(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)));
        assertEquals(stored.getCreatedAt(), stored.getUpdatedAt());
    }

    @Test void loginTokenAuthenticatesProtectedRoute() throws Exception {
        postJson("/api/auth/register", registration()).andExpect(status().isCreated());
        var login = postJson("/api/auth/login", Map.of("email", "test@example.invalid",
                "password", "synthetic-test-password")).andExpect(status().isOk());
        String token = responseToken(login);
        assertFalse(token.isBlank(), "Login must return a token");
        mvc.perform(get("/api/health").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string("Secret Shield API is running"));
    }

    @Test void duplicateRegistrationIs409WithNoExtraRow() throws Exception {
        postJson("/api/auth/register", registration()).andExpect(status().isCreated());
        postJson("/api/auth/register", registration()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertEquals(1, users.count());
    }

    @Test void realPostgresEnforcesUniqueEmail() {
        users.saveAndFlush(User.builder().username("synthetic-one").email("same@example.invalid")
                .password("synthetic-hash-placeholder").role(UserRole.DEVELOPER).build());
        assertThrows(DataIntegrityViolationException.class, () -> users.saveAndFlush(User.builder()
                .username("synthetic-two").email("same@example.invalid")
                .password("synthetic-hash-placeholder").role(UserRole.DEVELOPER).build()));
    }

    @Test void actualJpaUpdatePreservesCreationTimeAndRefreshesUpdateTime() {
        User stored = users.saveAndFlush(User.builder().username("synthetic-before")
                .email("update@example.invalid").password("synthetic-hash-placeholder").role(UserRole.DEVELOPER).build());
        entities.refresh(stored);
        LocalDateTime created = stored.getCreatedAt();
        LocalDateTime updated = stored.getUpdatedAt();
        stored.setUsername("synthetic-after");
        entities.flush();
        entities.clear();
        User reloaded = users.findByEmail("update@example.invalid").orElseThrow();
        assertEquals(created, reloaded.getCreatedAt());
        assertTrue(reloaded.getUpdatedAt().isAfter(updated));
    }
}
