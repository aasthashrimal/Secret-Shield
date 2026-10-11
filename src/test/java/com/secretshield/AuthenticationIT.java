package com.secretshield;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.secretshield.repository.UserRepository;
import com.secretshield.security.JwtService;
import com.secretshield.support.PostgresApiTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationIT extends PostgresApiTest {
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired ApplicationContext context;
    private final String password = "synthetic-test-password";

    private String registerAndLogin() throws Exception {
        postJson("/api/auth/register", Map.of("username", "synthetic-auth", "email", "auth@example.invalid", "password", password))
                .andExpect(status().isCreated());
        return responseToken(postJson("/api/auth/login", Map.of("email", "auth@example.invalid", "password", password))
                .andExpect(status().isOk()));
    }

    @Test void normalizedRegistrationLoginAndMeExposeOnlySafeFields() throws Exception {
        postJson("/api/auth/register", Map.of("username", " synthetic-auth ", "email", " AUTH@EXAMPLE.INVALID ", "password", password))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("auth@example.invalid"))
                .andExpect(jsonPath("$.username").value("synthetic-auth"));
        var stored = users.findByEmail("auth@example.invalid").orElseThrow();
        assertTrue(passwords.matches(password, stored.getPassword()), "Password must be hashed with BCrypt");
        String token = responseToken(postJson("/api/auth/login", Map.of("email", " AUTH@EXAMPLE.INVALID ", "password", password))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist()));
        var response = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        assertEquals(4, body.size());
        assertEquals(stored.getId().toString(), body.get("id").asString());
        assertEquals("synthetic-auth", body.get("username").asString());
        assertEquals("auth@example.invalid", body.get("email").asString());
        assertEquals("DEVELOPER", body.get("role").asString());
        assertFalse(response.getContentAsString().contains(stored.getPassword()), "Response must omit hash");
    }

    @Test void normalizedDuplicateEmailAndDuplicateUsernameAre409() throws Exception {
        registerAndLogin();
        postJson("/api/auth/register", Map.of("username", "different-name", "email", " AUTH@EXAMPLE.INVALID ", "password", password))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        postJson("/api/auth/register", Map.of("username", "synthetic-auth", "email", "other@example.invalid", "password", password))
                .andExpect(status().isConflict());
        assertEquals(1, users.count());
    }

    @Test void invalidRegistrationBoundsAndMultibytePasswordsAre400() throws Exception {
        for (String username : new String[]{"ab", "a".repeat(51), "invalid name"}) {
            postJson("/api/auth/register", Map.of("username", username, "email", "bounds@example.invalid", "password", password))
                    .andExpect(status().isBadRequest());
        }
        for (String value : new String[]{"short", "a".repeat(73), "é".repeat(37), "        "}) {
            postJson("/api/auth/register", Map.of("username", "bounds-user", "email", "bounds@example.invalid", "password", value))
                    .andExpect(status().isBadRequest());
        }
        postJson("/api/auth/register", Map.of("username", "bounds-user", "email", "not-an-email", "password", password))
                .andExpect(status().isBadRequest());
        assertEquals(0, users.count());
        postJson("/api/auth/register", Map.of("username", "bounds-user", "email", "bounds@example.invalid", "password", "é".repeat(36)))
                .andExpect(status().isCreated());
    }

    @Test void wrongAndUnknownLoginHaveSameSafe401() throws Exception {
        registerAndLogin();
        String first = postJson("/api/auth/login", Map.of("email", "auth@example.invalid", "password", "synthetic-wrong-password"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String second = postJson("/api/auth/login", Map.of("email", "unknown@example.invalid", "password", password))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals(first, second);
        postJson("/api/auth/login", Map.of("email", "auth@example.invalid", "password", "é".repeat(37)))
                .andExpect(status().isBadRequest());
    }

    @Test void missingMalformedTamperedExpiredAndUnknownUserTokensAre401() throws Exception {
        String valid = registerAndLogin();
        String expired = Jwts.builder().subject("auth@example.invalid")
                .issuedAt(new Date(System.currentTimeMillis() - 120000)).expiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(Keys.hmacShaKeyFor("synthetic-test-only-signing-key-never-use-outside-tests".getBytes(StandardCharsets.UTF_8))).compact();
        String[] parts = valid.split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        String tampered = parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        for (String path : new String[]{"/api/auth/me", "/api/health"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
            for (String token : new String[]{"synthetic-invalid", tampered, expired, jwt.generateToken("missing@example.invalid", "ADMIN")}) {
                mvc.perform(get(path).header("Authorization", "Bearer " + token))
                        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                        .andExpect(jsonPath("$.status").value(401));
            }
        }
    }

    @Test void tokenRoleDoesNotOverrideDatabaseRoleAndRemovedUserIsRejected() throws Exception {
        registerAndLogin();
        String forgedRole = jwt.generateToken("auth@example.invalid", "ADMIN");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forgedRole))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("DEVELOPER"));
        users.deleteAll(); users.flush();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forgedRole)).andExpect(status().isUnauthorized());
    }

    @Test void corsPreflightAndAuthenticatedBrowserRequestWorkWithoutCookies() throws Exception {
        mvc.perform(options("/api/auth/me").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        String token = registerAndLogin();
        mvc.perform(get("/api/auth/me").header("Origin", "http://localhost:5173").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/api/auth/me").header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test void unsupportedOriginsMethodsAndHeadersAreRejectedWithSafe403() throws Exception {
        mvc.perform(options("/api/auth/me").header("Origin", "https://untrusted.example.invalid")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get("/api/health/live").header("Origin", "http://127.0.0.1:5173"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(options("/api/auth/me").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(options("/api/auth/me").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "X-Unsupported"))
                .andExpect(status().isForbidden());
    }

    @Test void defaultInMemoryUserAndBasicFormLoginPathsAreAbsent() throws Exception {
        assertTrue(context.getBeansOfType(UserDetailsService.class).isEmpty(), "No default in-memory users");
        String basic = Base64.getEncoder().encodeToString("user:synthetic-unused-test-password".getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/api/auth/me").header("Authorization", "Basic " + basic)).andExpect(status().isUnauthorized());
        mvc.perform(get("/login")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
