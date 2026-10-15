package com.secretshield;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.secretshield.entity.*;
import com.secretshield.repository.*;
import com.secretshield.security.JwtService;
import com.secretshield.support.PostgresApiTest;

import tools.jackson.databind.JsonNode;

class IncidentManagementIT extends PostgresApiTest {
    @Autowired UserRepository users;
    @Autowired ProjectRepositoryRepository repositories;
    @Autowired IncidentRepository incidents;
    @Autowired FindingRepository findings;
    @Autowired AuditEventRepository events;
    @Autowired JwtService tokens;

    @Test
    void recurrenceReopensResolvedIncidentAndIdenticalReplayCreatesNoHistory() throws Exception {
        User owner = user("incident-owner", UserRole.DEVELOPER);
        ProjectRepository repository = repository(owner, "lifecycle");
        String credential = credential(owner, repository);

        JsonNode first = submit(repository, credential, 1).get("id");
        assertEquals(1, incidents.count());
        assertEquals(1, findings.count());
        JsonNode incident = list(owner, repository, "?status=OPEN&severity=LOW").get("content").get(0);
        String id = incident.get("id").asText();
        assertEquals("0", incident.get("version").asText());
        assertEquals(id, findingId(owner, repository, first.asText()));
        assertEquals(1, listEvents(owner, repository, "?operation=INCIDENT_CREATED").get("totalElements").asInt());

        JsonNode resolved = transition(owner, repository, id, "RESOLVED", 0, "Fixture removed; demo credential rotated.", 200);
        assertEquals("RESOLVED", resolved.get("status").asText());
        assertEquals(1, resolved.get("version").asInt());
        assertEquals("USER", resolved.get("lastActorType").asText());
        assertEquals(owner.getId().toString(), resolved.get("lastActorReference").asText());

        int auditCount = events.findAll((root, query, cb) ->
                cb.equal(root.get("repository").get("id"), repository.getId())).size();
        submit(repository, credential, 1, true);
        assertEquals(auditCount, events.findAll((root, query, cb) ->
                cb.equal(root.get("repository").get("id"), repository.getId())).size());
        assertEquals(1, findings.count());

        JsonNode second = submit(repository, credential, 2);
        JsonNode reopened = detail(owner, repository, id);
        assertEquals("OPEN", reopened.get("status").asText());
        assertTrue(reopened.get("reviewReason").isNull());
        assertEquals("SYSTEM", reopened.get("lastActorType").asText());
        assertEquals(2, reopened.get("version").asInt());
        assertEquals(id, findingId(owner, repository, second.get("id").asText()));
        assertEquals(1, incidents.count());
        assertEquals(2, findings.count());
        assertEquals(1, listEvents(owner, repository, "?operation=INCIDENT_REOPENED").get("totalElements").asInt());
        assertEquals("FINDINGS_DETECTED", second.get("status").asText());
    }

    @Test
    void reviewAuthorizationVersionAndIgnoredRecurrenceKeepScannerOutcome() throws Exception {
        User owner = user("incident-review", UserRole.DEVELOPER);
        User other = user("incident-other", UserRole.DEVELOPER);
        User admin = user("incident-admin", UserRole.ADMIN);
        ProjectRepository repository = repository(owner, "review");
        ProjectRepository otherRepository = repository(other, "other-review");
        String credential = credential(owner, repository);
        submit(repository, credential, 1);
        String id = list(owner, repository, "").get("content").get(0).get("id").asText();

        transition(owner, repository, id, "IGNORED", 0, "Known synthetic fixture", 403);
        mvc.perform(patch("/api/repositories/{id}/incidents/{incident}", repository.getId(), id)
                .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("status", "RESOLVED", "reason", "Safe review",
                        "version", 0, "scannerStatus", "CLEAN"))))
                .andExpect(status().isBadRequest());
        transition(owner, repository, id, "OPEN", 0, "Invalid same state", 409);
        transition(admin, repository, id, "IGNORED", 7, "Stale review", 409);
        JsonNode ignored = transition(admin, repository, id, "IGNORED", 0, "Known synthetic fixture", 200);
        assertEquals("IGNORED", ignored.get("status").asText());
        assertEquals("Known synthetic fixture", ignored.get("reviewReason").asText());

        JsonNode second = submit(repository, credential, 2);
        assertEquals("FINDINGS_DETECTED", second.get("status").asText());
        assertEquals("IGNORED", detail(owner, repository, id).get("status").asText());
        assertEquals(1, incidents.count());
        assertEquals(2, findings.count());

        mvc.perform(get("/api/repositories/{id}/incidents", repository.getId())
                .header("Authorization", bearer(other))).andExpect(status().isForbidden());
        mvc.perform(get("/api/repositories/{id}/audit-events", repository.getId())
                .header("Authorization", bearer(other))).andExpect(status().isForbidden());
        mvc.perform(get("/api/repositories/{id}/incidents/{incident}", otherRepository.getId(), id)
                .header("Authorization", bearer(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/repositories/{id}/incidents?status=BOGUS", repository.getId())
                .header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/repositories/{id}/audit-events?size=101", repository.getId())
                .header("Authorization", bearer(owner))).andExpect(status().isBadRequest());

        JsonNode opened = transition(owner, repository, id, "OPEN", 1, "Review withdrawn", 200);
        assertEquals("OPEN", opened.get("status").asText());
        assertEquals("Review withdrawn", opened.get("reviewReason").asText());
        assertEquals(1, listEvents(owner, repository, "?operation=INCIDENT_IGNORED&actorType=USER")
                .get("totalElements").asInt());
    }

    @Test
    void auditRowsIdentifyUserMachineAndSystemWithoutCredentialOrSourceText() throws Exception {
        User owner = user("incident-audit", UserRole.DEVELOPER);
        ProjectRepository repository = repository(owner, "audit");
        String credential = credential(owner, repository);
        submit(repository, credential, 1);

        JsonNode history = listEvents(owner, repository, "?size=100");
        String serialized = history.toString();
        assertFalse(serialized.contains(credential));
        assertFalse(serialized.contains("sensitive-source-value"));
        JsonNode machine = listEvents(owner, repository, "?actorType=MACHINE").get("content").get(0);
        assertEquals("SCAN_COMPLETED", machine.get("operation").asText());
        assertTrue(machine.get("actorUserId").isNull());
        assertFalse(machine.get("actorCredentialId").isNull());
        assertEquals(1, listEvents(owner, repository, "?actorType=SYSTEM&operation=INCIDENT_CREATED")
                .get("totalElements").asInt());
        assertEquals(1, listEvents(owner, repository, "?actorType=USER&operation=CI_CREDENTIAL_CREATED")
                .get("totalElements").asInt());
    }

    @Test
    void repositoryScanAndCredentialMutationsHaveAuditedHistory() throws Exception {
        User owner = user("history-owner", UserRole.DEVELOPER);
        var created = mvc.perform(post("/api/repositories")
                .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("githubOwner", "demo", "githubName", "history",
                        "defaultBranch", "main"))))
                .andExpect(status().isCreated()).andReturn();
        long id = Long.parseLong(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
        ProjectRepository repository = repositories.findById(id).orElseThrow();
        mvc.perform(patch("/api/repositories/{id}", id)
                .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("defaultBranch", "develop"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/repositories/{id}/scans", id)
                .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("branch", "develop",
                        "commitSha", "0123456789abcdef0123456789abcdef01234567"))))
                .andExpect(status().isCreated());
        String issued = credential(owner, repository);
        String credentialId = issued.split("\\.")[1];
        mvc.perform(post("/api/repositories/{id}/ci-credentials/{credentialId}/revoke", id, credentialId)
                .header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        mvc.perform(post("/api/repositories/{id}/deactivate", id)
                .header("Authorization", bearer(owner))).andExpect(status().isNoContent());

        JsonNode history = listEvents(owner, repository, "?size=100&actorType=USER");
        assertEquals(6, history.get("totalElements").asInt());
        for (String operation : List.of("REPOSITORY_CREATED", "REPOSITORY_UPDATED", "SCAN_CREATED",
                "CI_CREDENTIAL_CREATED", "CI_CREDENTIAL_REVOKED", "REPOSITORY_DEACTIVATED")) {
            assertEquals(1, listEvents(owner, repository, "?operation=" + operation)
                    .get("totalElements").asInt());
        }
        assertEquals(0, listEvents(owner, repository, "?from=2100-01-01T00:00:00Z")
                .get("totalElements").asInt());
    }

    private User user(String name, UserRole role) {
        return users.saveAndFlush(User.builder().username(name).email(name + "@example.invalid")
                .password("hash").role(role).build());
    }

    private ProjectRepository repository(User owner, String name) {
        return repositories.saveAndFlush(ProjectRepository.builder().owner(owner)
                .githubOwner("demo").githubName(name).canonicalUrl("https://github.com/demo/" + name)
                .defaultBranch("main").active(true).build());
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user.getEmail(), user.getRole().name());
    }

    private String credential(User owner, ProjectRepository repository) throws Exception {
        var response = mvc.perform(post("/api/repositories/{id}/ci-credentials", repository.getId())
                .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("credential").asText();
    }

    private JsonNode submit(ProjectRepository repository, String credential, int attempt) throws Exception {
        return submit(repository, credential, attempt, false);
    }

    private JsonNode submit(ProjectRepository repository, String credential, int attempt,
            boolean replay) throws Exception {
        var body = Map.of("runId", "incident-run", "runAttempt", attempt,
                "commitSha", "0123456789abcdef0123456789abcdef01234567", "branch", "main",
                "trigger", "CI_PUSH", "scannerVersion", "8.30.0", "status", "FINDINGS_DETECTED",
                "findings", List.of(Map.of("ruleId", "synthetic-low", "filePath", "config/demo.txt",
                        "line", 2, "sanitizedDescription", "sensitive-source-value")));
        var response = mvc.perform(post("/api/ci/repositories/{id}/scan-results", repository.getId())
                .header("X-Secret-Shield-Credential", credential)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(replay ? status().isOk() : status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsString());
    }

    private JsonNode list(User actor, ProjectRepository repository, String query) throws Exception {
        var result = mvc.perform(get("/api/repositories/{id}/incidents" + query, repository.getId())
                .header("Authorization", bearer(actor))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode detail(User actor, ProjectRepository repository, String id) throws Exception {
        var result = mvc.perform(get("/api/repositories/{id}/incidents/{incident}", repository.getId(), id)
                .header("Authorization", bearer(actor))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String findingId(User actor, ProjectRepository repository, String scanId) throws Exception {
        var result = mvc.perform(get("/api/repositories/{id}/scans/{scan}/findings", repository.getId(), scanId)
                .header("Authorization", bearer(actor))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("content").get(0)
                .get("incidentId").asText();
    }

    private JsonNode transition(User actor, ProjectRepository repository, String id,
            String state, long version, String reason, int expected) throws Exception {
        var result = mvc.perform(patch("/api/repositories/{id}/incidents/{incident}", repository.getId(), id)
                .header("Authorization", bearer(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("status", state, "reason", reason,
                        "version", version)))).andExpect(status().is(expected)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode listEvents(User actor, ProjectRepository repository, String query) throws Exception {
        var result = mvc.perform(get("/api/repositories/{id}/audit-events" + query, repository.getId())
                .header("Authorization", bearer(actor))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
