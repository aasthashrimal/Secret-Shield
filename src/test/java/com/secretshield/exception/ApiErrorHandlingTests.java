package com.secretshield.exception;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.secretshield.config.SecurityConfig;
import com.secretshield.controller.AuthController;
import com.secretshield.dto.ApiError;
import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;
import com.secretshield.repository.UserRepository;
import com.secretshield.security.JsonSecurityErrorHandler;
import com.secretshield.security.JwtAuthenticationFilter;
import com.secretshield.security.JwtService;
import com.secretshield.service.AuthService;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, ApiErrorHandlingTests.FailureController.class},
        properties = {"debug=false", "logging.level.root=WARN",
                "jwt.secret=synthetic-test-only-signing-key-never-use-outside-tests",
                "spring.security.user.password=synthetic-unused-test-password"})
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JsonSecurityErrorHandler.class,
        GlobalExceptionHandler.class, ApiErrorHandlingTests.ForbiddenFilterConfig.class,
        ApiErrorHandlingTests.FailureController.class})
class ApiErrorHandlingTests {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService authService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;

    private ResultActions expectError(ResultActions result, int statusCode) throws Exception {
        ApiError expected = ApiError.of(org.springframework.http.HttpStatus.valueOf(statusCode));
        return result.andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.code").value(expected.code()))
                .andExpect(jsonPath("$.message").value(expected.message()))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("sensitive-marker"))));
    }

    @Test void invalidRegistrationIs400() throws Exception {
        expectError(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"email\":\"sensitive-marker\",\"password\":\"short\"}")), 400);
    }

    @Test void malformedJsonIs400() throws Exception {
        expectError(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"sensitive-marker\",")), 400);
    }

    @Test void missingRequestBodyIs400() throws Exception {
        expectError(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)), 400);
    }

    @Test void invalidCredentialsAre401() throws Exception {
        when(authService.login("test@example.invalid", "synthetic-password"))
                .thenThrow(new AuthenticationException("sensitive-marker"));
        expectError(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.invalid\",\"password\":\"synthetic-password\"}")), 401);
    }

    @Test void duplicateRegistrationIs409() throws Exception {
        when(authService.register("tester", "test@example.invalid", "synthetic-password"))
                .thenThrow(new ConflictException("sensitive-marker"));
        expectError(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"tester\",\"email\":\"test@example.invalid\",\"password\":\"synthetic-password\"}")), 409);
    }

    @Test void missingAuthenticationIsJson401() throws Exception {
        expectError(mvc.perform(get("/test/failure/not-found")), 401)
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test void invalidBearerIsJson401() throws Exception {
        expectError(mvc.perform(get("/test/failure/not-found")
                .header("Authorization", "Bearer synthetic-invalid-token")), 401);
    }

    @Test void unknownUserBearerIsJson401() throws Exception {
        when(jwtService.isTokenValid("synthetic-token")).thenReturn(true);
        when(jwtService.extractUsername("synthetic-token")).thenReturn("unknown@example.invalid");
        when(userRepository.findByEmail("unknown@example.invalid")).thenReturn(java.util.Optional.empty());
        expectError(mvc.perform(get("/test/failure/not-found")
                .header("Authorization", "Bearer synthetic-token")), 401);
    }

    @Test void tokenParsingFailureIsJson401() throws Exception {
        when(jwtService.isTokenValid("synthetic-token")).thenReturn(true);
        when(jwtService.extractUsername("synthetic-token"))
                .thenThrow(new io.jsonwebtoken.JwtException("sensitive-marker"));
        expectError(mvc.perform(get("/test/failure/not-found")
                .header("Authorization", "Bearer synthetic-token")), 401);
    }

    @Test void authenticationDatabaseFailureIsSafe500() throws Exception {
        when(jwtService.isTokenValid("synthetic-token")).thenReturn(true);
        when(jwtService.extractUsername("synthetic-token")).thenReturn("test@example.invalid");
        when(userRepository.findByEmail("test@example.invalid"))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("sensitive-marker"));
        expectError(mvc.perform(get("/test/failure/not-found")
                .header("Authorization", "Bearer synthetic-token")), 500);
    }

    @Test void validBearerReachesController() throws Exception {
        when(jwtService.isTokenValid("synthetic-token")).thenReturn(true);
        when(jwtService.extractUsername("synthetic-token")).thenReturn("test@example.invalid");
        when(userRepository.findByEmail("test@example.invalid"))
                .thenReturn(java.util.Optional.of(User.builder().email("test@example.invalid")
                        .role(UserRole.DEVELOPER).build()));
        expectError(mvc.perform(get("/test/failure/not-found")
                .header("Authorization", "Bearer synthetic-token")), 404);
    }

    @Test void deniedBySecurityFilterIsJson403() throws Exception {
        expectError(mvc.perform(get("/test/filter-forbidden").with(user("tester"))), 403);
    }

    @Test void deniedByControllerIsJson403() throws Exception {
        expectError(mvc.perform(get("/test/failure/forbidden").with(user("tester"))), 403);
    }

    @Test void missingResourceIs404() throws Exception {
        expectError(mvc.perform(get("/test/failure/not-found").with(user("tester"))), 404);
    }

    @Test void missingRouteIs404() throws Exception {
        expectError(mvc.perform(get("/api/does-not-exist").with(user("tester"))), 404);
    }

    @Test void unexpectedFailureIsSafe500() throws Exception {
        expectError(mvc.perform(get("/test/failure/unexpected").with(user("tester"))), 500);
    }

    @Test void databaseUniqueViolationIs409() throws Exception {
        expectError(mvc.perform(get("/test/failure/duplicate-sql").with(user("tester"))), 409);
    }

    @Test void otherDatabaseConstraintIsSafe500() throws Exception {
        expectError(mvc.perform(get("/test/failure/other-sql").with(user("tester"))), 500);
    }

    @Test void unsupportedMethodRetains405AndAllowHeader() throws Exception {
        expectError(mvc.perform(get("/api/auth/login")), 405)
                .andExpect(header().string("Allow", "POST"));
    }

    @Test void unsupportedContentTypeIs415() throws Exception {
        expectError(mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN)
                .content("sensitive-marker")), 415);
    }

    @Test void successResponseRemainsUnwrapped() throws Exception {
        when(authService.register("tester", "test@example.invalid", "synthetic-password"))
                .thenReturn(User.builder().id(1L).username("tester").email("test@example.invalid")
                        .role(UserRole.DEVELOPER).build());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"tester\",\"email\":\"test@example.invalid\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("tester"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.success").doesNotExist());
    }

    @RestController
    static class FailureController {
        @GetMapping("/test/failure/{kind}")
        public void fail(@PathVariable String kind) {
            switch (kind) {
                case "not-found" -> throw new ResourceNotFoundException("sensitive-marker");
                case "forbidden" -> throw new AccessDeniedException("sensitive-marker");
                case "duplicate-sql" -> throw new DataIntegrityViolationException("sensitive-marker",
                        new SQLException("sensitive-marker", "23505"));
                case "other-sql" -> throw new DataIntegrityViolationException("sensitive-marker",
                        new SQLException("sensitive-marker", "23502"));
                default -> throw new RuntimeException("sensitive-marker");
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ForbiddenFilterConfig {
        @Bean @Order(0)
        SecurityFilterChain forbiddenChain(HttpSecurity http, JsonSecurityErrorHandler errors) throws Exception {
            return http.securityMatcher("/test/filter-forbidden")
                    .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                    .build();
        }
    }
}
