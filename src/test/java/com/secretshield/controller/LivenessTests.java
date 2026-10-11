package com.secretshield.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.secretshield.config.SecurityConfig;
import com.secretshield.exception.GlobalExceptionHandler;
import com.secretshield.repository.UserRepository;
import com.secretshield.security.JsonSecurityErrorHandler;
import com.secretshield.security.JwtAuthenticationFilter;
import com.secretshield.security.JwtService;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HealthController.class,
        properties = {"spring.security.user.password=synthetic-unused-test-password",
                "jwt.secret=synthetic-test-only-signing-key-never-use-outside-tests",
                "debug=false", "logging.level.root=WARN"})
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JsonSecurityErrorHandler.class,
        GlobalExceptionHandler.class})
class LivenessTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService tokens;
    @MockitoBean UserRepository users;

    @Test void publicLivenessReturnsOnlyStatusWithoutDatabaseOrJwt() throws Exception {
        mvc.perform(get("/api/health/live"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"UP\"}",
                        org.springframework.test.json.JsonCompareMode.STRICT));
        verifyNoInteractions(tokens, users);
    }

    @Test void bearerHeaderCannotMakeLivenessDependOnAuthentication() throws Exception {
        mvc.perform(get("/api/health/live").header("Authorization", "Bearer synthetic-invalid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        verifyNoInteractions(tokens, users);
    }

    @Test void livenessWorksUnderServletContextPath() throws Exception {
        mvc.perform(get("/shield/api/health/live").contextPath("/shield")
                .header("Authorization", "Bearer synthetic-invalid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        verifyNoInteractions(tokens, users);
    }

    @Test void originalHealthEndpointRemainsProtected() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test void authenticatedOriginalHealthEndpointStillWorks() throws Exception {
        mvc.perform(get("/api/health").with(user("tester"))).andExpect(status().isOk())
                .andExpect(content().string("Secret Shield API is running"));
    }

    @Test void livenessPermissionDoesNotExposeOtherPathsOrMethods() throws Exception {
        mvc.perform(get("/api/health/live/details")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/health/live")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/private")).andExpect(status().isUnauthorized());
    }
}
