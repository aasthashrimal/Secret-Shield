package com.secretshield;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
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

class GateDecisionIT extends PostgresApiTest {
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
    private static final String OTHER_SHA = "fedcba9876543210fedcba9876543210fedcba98";
    @Autowired UserRepository users;
    @Autowired ProjectRepositoryRepository repositories;
    @Autowired ScanRepository scans;
    @Autowired IncidentRepository incidents;
    @Autowired JwtService tokens;

    @Test
    void allScanStatesAndMalformedCleanRecordsFailClosed() throws Exception {
        User owner = user("gate-states", UserRole.DEVELOPER);
        ProjectRepository repository = repository(owner, "states");
        assertGate(owner, repository, SHA, "BLOCK", "NO_SCAN", null);

        Scan scan = manual(repository, SHA, ScanStatus.QUEUED);
        assertGate(owner, repository, SHA, "BLOCK", "SCAN_PENDING", scan.getId());
        scan.setStatus(ScanStatus.RUNNING);
        scan.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        scans.saveAndFlush(scan);
        assertGate(owner, repository, SHA, "BLOCK", "SCAN_PENDING", scan.getId());

        scan.setStatus(ScanStatus.FAILED);
        scan.setFailureCode("SCANNER_TIMEOUT");
        scan.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        scans.saveAndFlush(scan);
        assertGate(owner, repository, SHA, "BLOCK", "SCAN_FAILED", scan.getId());

        scan.setStatus(ScanStatus.CLEAN);
        scan.setFailureCode(null);
        scans.saveAndFlush(scan);
        assertGate(owner, repository, SHA, "BLOCK", "SCAN_FAILED", scan.getId());
        scan.setScannerVersion("8.30.0");
        scans.saveAndFlush(scan);
        assertGate(owner, repository, SHA, "ALLOW", "CLEAN", scan.getId());
        scan.setScannerVersion("unsupported-version");
        scans.saveAndFlush(scan);
        assertGate(owner, repository, SHA, "BLOCK", "SCAN_FAILED", scan.getId());
    }

    @Test
    void exactCommitAndNewestScanDetermineGate() throws Exception {
        User owner = user("gate-commit", UserRole.DEVELOPER);
        ProjectRepository repository = repository(owner, "commit");
        Scan clean = manual(repository, SHA, ScanStatus.CLEAN);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        clean.setScannerVersion("8.30.0");
        clean.setStartedAt(now);
        clean.setCompletedAt(now);
        scans.saveAndFlush(clean);
        assertGate(owner, repository, SHA, "ALLOW", "CLEAN", clean.getId());
        assertGate(owner, repository, OTHER_SHA, "BLOCK", "NO_SCAN", null);

        Scan latest = manual(repository, SHA, ScanStatus.QUEUED);
        assertGate(owner, repository, SHA, "BLOCK", "SCAN_PENDING", latest.getId());
        latest.setStatus(ScanStatus.FAILED);
        latest.setScannerVersion("8.30.0");
        latest.setStartedAt(now);
        latest.setCompletedAt(now);
        latest.setFailureCode("SCANNER_TIMEOUT");
        scans.saveAndFlush(latest);
        assertGate(owner, repository, SHA, "BLOCK", "SCAN_FAILED", latest.getId());
        assertGate(owner, repository, OTHER_SHA, "BLOCK", "NO_SCAN", null);
    }

    @Test
    void findingResultRemainsBlockedAfterIncidentReviewAndRetry() throws Exception {
        User owner = user("gate-findings", UserRole.DEVELOPER);
        User admin = user("gate-admin", UserRole.ADMIN);
        ProjectRepository repository = repository(owner, "findings");
        String credential = credential(owner, repository);
        JsonNode first = submit(repository, credential, 1, "FINDINGS_DETECTED");
        assertGate(owner, repository, SHA, "BLOCK", "FINDINGS_DETECTED",
                Long.parseLong(first.get("id").asText()));

        Incident incident = incidents.findAll().getFirst();
        mvc.perform(patch("/api/repositories/{id}/incidents/{incident}", repository.getId(), incident.getId())
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("status", "IGNORED",
                        "reason", "Synthetic demonstration fixture", "version", 0))))
                .andExpect(status().isOk());
        assertGate(owner, repository, SHA, "BLOCK", "FINDINGS_DETECTED",
                Long.parseLong(first.get("id").asText()));

        // A corrupt CLEAN status cannot conceal persisted findings.
        Scan firstScan = scans.findById(Long.parseLong(first.get("id").asText())).orElseThrow();
        firstScan.setStatus(ScanStatus.CLEAN);
        scans.saveAndFlush(firstScan);
        assertGate(owner, repository, SHA, "BLOCK", "SCAN_FAILED", firstScan.getId());
        firstScan.setStatus(ScanStatus.FINDINGS_DETECTED);
        scans.saveAndFlush(firstScan);

        JsonNode second = submit(repository, credential, 2, "CLEAN");
        assertGate(owner, repository, SHA, "ALLOW", "CLEAN",
                Long.parseLong(second.get("id").asText()));
        JsonNode third = submit(repository, credential, 3, "FAILED");
        assertGate(owner, repository, SHA, "BLOCK", "SCAN_FAILED",
                Long.parseLong(third.get("id").asText()));
    }

    @Test
    void authorizationAndMalformedCommitAreEnforced() throws Exception {
        User owner = user("gate-owner", UserRole.DEVELOPER);
        User other = user("gate-other", UserRole.DEVELOPER);
        User admin = user("gate-manager", UserRole.ADMIN);
        ProjectRepository repository = repository(owner, "owned");
        mvc.perform(get("/api/repositories/{id}/gate?commitSha=" + SHA, repository.getId()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/repositories/{id}/gate?commitSha=" + SHA, repository.getId())
                .header("Authorization", bearer(other))).andExpect(status().isForbidden());
        assertGate(admin, repository, SHA, "BLOCK", "NO_SCAN", null);
        mvc.perform(get("/api/repositories/{id}/gate?commitSha=bad", repository.getId())
                .header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/repositories/{id}/gate", repository.getId())
                .header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
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

    private Scan manual(ProjectRepository repository, String sha, ScanStatus status) {
        return scans.saveAndFlush(Scan.builder().repository(repository).branch("main")
                .commitSha(sha).trigger(ScanTrigger.MANUAL).status(status).build());
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user.getEmail(), user.getRole().name());
    }

    private JsonNode gate(User user, ProjectRepository repository, String sha) throws Exception {
        var result = mvc.perform(get("/api/repositories/{id}/gate?commitSha=" + sha, repository.getId())
                .header("Authorization", bearer(user))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private void assertGate(User user, ProjectRepository repository, String sha,
            String outcome, String reason, Long scanId) throws Exception {
        JsonNode decision = gate(user, repository, sha);
        assertEquals(repository.getId().toString(), decision.get("repositoryId").asText());
        assertEquals(sha, decision.get("commitSha").asText());
        assertEquals(outcome, decision.get("outcome").asText());
        assertEquals(reason, decision.get("reason").asText());
        if (scanId == null) assertTrue(decision.get("scanId").isNull());
        else assertEquals(scanId.toString(), decision.get("scanId").asText());
    }

    private String credential(User owner, ProjectRepository repository) throws Exception {
        var result = mvc.perform(post("/api/repositories/{id}/ci-credentials", repository.getId())
                .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("credential").asText();
    }

    private JsonNode submit(ProjectRepository repository, String credential, int attempt,
            String state) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("runId", "gate-run");
        body.put("runAttempt", attempt);
        body.put("commitSha", SHA);
        body.put("branch", "main");
        body.put("trigger", "CI_PUSH");
        body.put("scannerVersion", "8.30.0");
        body.put("status", state);
        body.put("findings", state.equals("FINDINGS_DETECTED")
                ? List.of(Map.of("ruleId", "synthetic-low", "filePath", "config/demo.txt",
                        "line", 2, "sanitizedDescription", "Synthetic metadata")) : List.of());
        if (state.equals("FAILED")) body.put("failureCode", "SCANNER_TIMEOUT");
        var result = mvc.perform(post("/api/ci/repositories/{id}/scan-results", repository.getId())
                .header("X-Secret-Shield-Credential", credential)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
