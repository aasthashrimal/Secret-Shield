package com.secretshield;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.entity.ProjectRepository;
import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;
import com.secretshield.repository.ProjectRepositoryRepository;
import com.secretshield.repository.UserRepository;
import com.secretshield.security.JwtService;
import com.secretshield.service.FakeNotificationProvider;
import com.secretshield.service.NotificationDispatcher;
import com.secretshield.support.PostgresApiTest;

import tools.jackson.databind.JsonNode;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "secretshield.notifications.provider=fake",
        "secretshield.notifications.recipients=security=alerts@example.invalid",
        "secretshield.notifications.scheduled=false"
})
class NotificationManagementIT extends PostgresApiTest {
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
    @Autowired UserRepository users;
    @Autowired ProjectRepositoryRepository repositories;
    @Autowired JwtService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeNotificationProvider fake;
    @Autowired NotificationDispatcher dispatcher;
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdRepositoryIds = new ArrayList<>();

    @BeforeEach void resetFake() { fake.clear(); }
    @AfterEach void clearFixtures() {
        fake.clear();
        for (Long repositoryId : createdRepositoryIds) {
            jdbc.update("DELETE FROM notification_deliveries WHERE incident_id IN "
                    + "(SELECT id FROM incidents WHERE repository_id = ?)", repositoryId);
            jdbc.update("DELETE FROM findings WHERE scan_id IN "
                    + "(SELECT id FROM scans WHERE repository_id = ?)", repositoryId);
            jdbc.update("DELETE FROM audit_events WHERE repository_id = ?", repositoryId);
            jdbc.update("DELETE FROM incidents WHERE repository_id = ?", repositoryId);
            jdbc.update("DELETE FROM scans WHERE repository_id = ?", repositoryId);
            jdbc.update("DELETE FROM ci_credentials WHERE repository_id = ?", repositoryId);
            jdbc.update("DELETE FROM project_repositories WHERE id = ?", repositoryId);
        }
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void highRiskEventsAreQueuedAfterCommitAndReplaysDoNotDuplicateAlerts() throws Exception {
        User owner = user("notify-owner", UserRole.DEVELOPER);
        User other = user("notify-other", UserRole.DEVELOPER);
        User admin = user("notify-admin", UserRole.ADMIN);
        ProjectRepository repository = repository(owner, "threshold");
        ProjectRepository otherRepository = repository(other, "another");
        String credential = issue(owner, repository);
        String callerText = "sensitive-caller-text-must-not-be-sent";
        var findings = List.of(
                finding("generic-api-key", "config/high.txt", callerText),
                finding("private-key", "config/critical.txt", callerText),
                finding("synthetic-low", "config/low.txt", callerText));

        JsonNode first = submit(repository, credential, 1, findings, 201);
        assertEquals("FINDINGS_DETECTED", first.get("status").asText());
        assertEquals(2, deliveryCount(repository));
        assertTrue(fake.sentMessages().isEmpty(), "No provider call may occur in the ingest transaction");

        JsonNode incidentList = incidents(owner, repository);
        assertEquals(3, incidentList.get("totalElements").asInt());
        String highId = incidentId(incidentList, "HIGH");
        String lowId = incidentId(incidentList, "LOW");
        assertEquals(1, notifications(owner, repository, highId).get("totalElements").asInt());
        assertEquals(0, notifications(owner, repository, lowId).get("totalElements").asInt());

        assertEquals(2, dispatcher.dispatchAvailable());
        assertEquals(2, fake.sentMessages().size());
        for (var message : fake.sentMessages()) {
            assertEquals("alerts@example.invalid", message.recipient());
            assertTrue(message.body().contains("demo/threshold"));
            assertTrue(message.body().contains("Review the incident: http://localhost:8080/api/repositories/"));
            assertTrue(message.body().contains("Rotate or revoke"));
            assertFalse(message.subject().contains(callerText));
            assertFalse(message.body().contains(callerText));
            assertFalse(message.body().contains(credential));
        }
        String history = notifications(owner, repository, highId).toString();
        assertTrue(history.contains("SENT"));
        assertTrue(history.contains("security"));
        assertFalse(history.contains("alerts@example.invalid"));
        assertFalse(history.contains(callerText));

        submit(repository, credential, 1, findings, 200);
        assertEquals(2, deliveryCount(repository));
        assertEquals(0, dispatcher.dispatchAvailable());
        submit(repository, credential, 2, List.of(findings.getFirst()), 201);
        assertEquals(3, deliveryCount(repository));
        assertEquals(2, notifications(owner, repository, highId).get("totalElements").asInt());
        assertEquals(1, dispatcher.dispatchAvailable());
        assertEquals(3, fake.sentMessages().size());

        mvc.perform(get("/api/repositories/{id}/incidents/{incident}/notifications",
                repository.getId(), highId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/repositories/{id}/incidents/{incident}/notifications",
                repository.getId(), highId).header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/repositories/{id}/incidents/{incident}/notifications",
                otherRepository.getId(), highId).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/repositories/{id}/incidents/{incident}/notifications?status=SENT&size=1",
                repository.getId(), highId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/repositories/{id}/incidents/{incident}/notifications?status=UNSAFE",
                repository.getId(), highId).header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void providerFailureRetriesAtMostThreeTimesAndDoesNotChangeTheGate() throws Exception {
        User owner = user("notify-retry", UserRole.DEVELOPER);
        ProjectRepository repository = repository(owner, "retry");
        String credential = issue(owner, repository);
        fake.failNext(2);
        submit(repository, credential, 1,
                List.of(finding("generic-api-key", "config/retry.txt", "caller-secret")), 201);
        String incidentId = incidentId(incidents(owner, repository), "HIGH");
        assertEquals(1, dispatcher.dispatchAvailable());
        JsonNode failed = notifications(owner, repository, incidentId).get("content").get(0);
        assertEquals("FAILED", failed.get("status").asText());
        assertEquals(1, failed.get("attemptCount").asInt());
        assertEquals("SIMULATED_FAILURE", failed.get("safeErrorCode").asText());
        assertEquals(0, dispatcher.dispatchAvailable(), "Retry backoff must be respected");
        double retrySeconds = jdbc.queryForObject("""
                SELECT EXTRACT(EPOCH FROM (n.next_attempt_at - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')))
                FROM notification_deliveries n JOIN incidents i ON i.id = n.incident_id
                WHERE i.repository_id = ? AND n.status = 'FAILED'
                """, Double.class, repository.getId());
        assertTrue(retrySeconds > 0 && retrySeconds <= 35,
                "First retry should be scheduled about 30 seconds from the database clock; actual "
                        + retrySeconds + " seconds");
        assertBlocked(owner, repository);

        makeDue(repository);
        assertEquals(1, dispatcher.dispatchAvailable());
        makeDue(repository);
        assertEquals(1, dispatcher.dispatchAvailable());
        JsonNode sent = notifications(owner, repository, incidentId).get("content").get(0);
        assertEquals("SENT", sent.get("status").asText());
        assertEquals(3, sent.get("attemptCount").asInt());
        assertEquals(1, fake.sentMessages().size());
        assertEquals(0, dispatcher.dispatchAvailable());
        assertBlocked(owner, repository);

        fake.failNext(3);
        submit(repository, credential, 2,
                List.of(finding("private-key", "config/exhausted.txt", "another-secret")), 201);
        assertEquals(1, dispatcher.dispatchAvailable());
        makeDue(repository);
        assertEquals(1, dispatcher.dispatchAvailable());
        makeDue(repository);
        assertEquals(1, dispatcher.dispatchAvailable());
        assertEquals(0, dispatcher.dispatchAvailable());
        String criticalId = incidentId(incidents(owner, repository), "CRITICAL");
        JsonNode exhausted = notifications(owner, repository, criticalId).get("content").get(0);
        assertEquals("FAILED", exhausted.get("status").asText());
        assertEquals(3, exhausted.get("attemptCount").asInt());
        assertEquals("SIMULATED_FAILURE", exhausted.get("safeErrorCode").asText());
        assertBlocked(owner, repository);
    }

    private User user(String name, UserRole role) {
        User saved = users.saveAndFlush(User.builder().username(name).email(name + "@example.invalid")
                .password("hash").role(role).build());
        createdUserIds.add(saved.getId());
        return saved;
    }

    private ProjectRepository repository(User owner, String name) {
        ProjectRepository saved = repositories.saveAndFlush(ProjectRepository.builder().owner(owner)
                .githubOwner("demo").githubName(name).canonicalUrl("https://github.com/demo/" + name)
                .defaultBranch("main").active(true).build());
        createdRepositoryIds.add(saved.getId());
        return saved;
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user.getEmail(), user.getRole().name());
    }

    private String issue(User owner, ProjectRepository repository) throws Exception {
        var response = mvc.perform(post("/api/repositories/{id}/ci-credentials", repository.getId())
                .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("credential").asText();
    }

    private JsonNode submit(ProjectRepository repository, String credential, int attempt,
            List<?> findings, int expectedStatus) throws Exception {
        var body = Map.of("runId", "notification-run", "runAttempt", attempt,
                "commitSha", SHA, "branch", "main", "trigger", "CI_PUSH",
                "scannerVersion", "8.30.0", "status", "FINDINGS_DETECTED", "findings", findings);
        var response = mvc.perform(post("/api/ci/repositories/{id}/scan-results", repository.getId())
                .header("X-Secret-Shield-Credential", credential)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().is(expectedStatus)).andReturn();
        return json.readTree(response.getResponse().getContentAsString());
    }

    private static Map<String, Object> finding(String rule, String path, String text) {
        return Map.of("ruleId", rule, "filePath", path, "line", 1,
                "sanitizedDescription", text);
    }

    private JsonNode incidents(User user, ProjectRepository repository) throws Exception {
        var response = mvc.perform(get("/api/repositories/{id}/incidents", repository.getId())
                .header("Authorization", bearer(user))).andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString());
    }

    private JsonNode notifications(User user, ProjectRepository repository, String incidentId)
            throws Exception {
        var response = mvc.perform(get("/api/repositories/{id}/incidents/{incident}/notifications",
                repository.getId(), incidentId).header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString());
    }

    private static String incidentId(JsonNode page, String severity) {
        for (JsonNode incident : page.get("content")) {
            if (severity.equals(incident.get("severity").asText())) return incident.get("id").asText();
        }
        throw new AssertionError("Missing " + severity + " incident");
    }

    private int deliveryCount(ProjectRepository repository) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM notification_deliveries n
                JOIN incidents i ON i.id = n.incident_id WHERE i.repository_id = ?
                """, Integer.class, repository.getId());
    }

    private void makeDue(ProjectRepository repository) {
        int updated = jdbc.update("""
                UPDATE notification_deliveries SET next_attempt_at = TIMESTAMP '2000-01-01 00:00:00'
                WHERE incident_id IN (SELECT id FROM incidents WHERE repository_id = ?)
                  AND status = 'FAILED' AND attempt_count < 3
                """, repository.getId());
        assertEquals(1, updated, "Expected one retryable delivery");
    }

    private void assertBlocked(User owner, ProjectRepository repository) throws Exception {
        mvc.perform(get("/api/repositories/{id}/gate?commitSha=" + SHA, repository.getId())
                .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("BLOCK"))
                .andExpect(jsonPath("$.reason").value("FINDINGS_DETECTED"));
    }
}
