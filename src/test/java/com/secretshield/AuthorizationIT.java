package com.secretshield;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;
import com.secretshield.repository.UserRepository;
import com.secretshield.security.JwtService;
import com.secretshield.service.AdminBootstrapService;
import com.secretshield.support.PostgresApiTest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthorizationIT extends PostgresApiTest {
    @Autowired UserRepository users;
    @Autowired JwtService tokens;
    @Autowired AdminBootstrapService bootstrap;
    @Autowired JdbcTemplate jdbc;

    private User user(String name, UserRole role) {
        return users.saveAndFlush(User.builder().username(name)
                .email(name + "@example.invalid").password("synthetic-hash")
                .role(role).build());
    }

    private String token(User user) {
        return tokens.generateToken(user.getEmail(), user.getRole().name());
    }

    @Test void publicRegistrationCannotAssignAdministratorRole() throws Exception {
        postJson("/api/auth/register", Map.of("username", "synthetic-public",
                "email", "public@example.invalid", "password", "synthetic-password",
                "role", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
        assertEquals(UserRole.DEVELOPER, users.findByEmail("public@example.invalid").orElseThrow().getRole());
    }

    @Test void developerGets403AndAdminCanSafelyInspectAnotherUser() throws Exception {
        User developer = user("synthetic-developer", UserRole.DEVELOPER);
        User target = user("synthetic-target", UserRole.DEVELOPER);
        User admin = user("synthetic-admin", UserRole.ADMIN);

        mvc.perform(get("/api/admin/users/{id}", target.getId())
                        .header("Authorization", "Bearer " + token(developer)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));

        var result = mvc.perform(get("/api/admin/users/{id}", target.getId())
                        .header("Authorization", "Bearer " + token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(target.getId().toString()))
                .andExpect(jsonPath("$.username").value(target.getUsername()))
                .andExpect(jsonPath("$.email").value(target.getEmail()))
                .andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andReturn();
        assertEquals(4, json.readTree(result.getResponse().getContentAsString()).size());
    }

    @Test void adminLookupAuthenticatesBeforeHidingUnknownUsers() throws Exception {
        User developer = user("synthetic-developer", UserRole.DEVELOPER);
        User admin = user("synthetic-admin", UserRole.ADMIN);
        mvc.perform(get("/api/admin/users/999999")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users/999999").header("Authorization", "Bearer " + token(developer)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/999999").header("Authorization", "Bearer " + token(admin)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        for (String invalid : new String[]{"0", "01", "-1", "9223372036854775808"}) {
            mvc.perform(get("/api/admin/users/{id}", invalid)
                            .header("Authorization", "Bearer " + token(admin)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
    }

    @Test void oneTimeBootstrapPromotesOnlyExistingNormalizedEmailAndIsIdempotent() {
        User developer = user("synthetic-bootstrap", UserRole.DEVELOPER);
        bootstrap.promoteExistingUser(" SYNTHETIC-BOOTSTRAP@EXAMPLE.INVALID ");
        assertEquals(UserRole.ADMIN, users.findById(developer.getId()).orElseThrow().getRole());
        assertDoesNotThrow(() -> bootstrap.promoteExistingUser("synthetic-bootstrap@example.invalid"));
        var failure = assertThrows(IllegalStateException.class,
                () -> bootstrap.promoteExistingUser("missing-private@example.invalid"));
        assertFalse(failure.getMessage().contains("missing-private"));
        assertThrows(IllegalArgumentException.class, () -> bootstrap.promoteExistingUser(" "));
    }

    @Test void databaseRejectsFreeFormRoleValues() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO users(username,email,password,role,created_at,updated_at)
                VALUES (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, "synthetic-invalid-role", "invalid-role@example.invalid",
                "synthetic-hash", "SUPERUSER"));
    }
}
