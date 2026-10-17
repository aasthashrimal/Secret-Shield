package com.secretshield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.secretshield.entity.*;
import com.secretshield.repository.*;
import com.secretshield.security.JwtService;
import com.secretshield.service.DashboardService;
import com.secretshield.support.PostgresApiTest;

import tools.jackson.databind.JsonNode;

class DashboardIT extends PostgresApiTest {
    private static final String SHA_A = "0123456789abcdef0123456789abcdef01234567";
    private static final String SHA_B = "fedcba9876543210fedcba9876543210fedcba98";
    @Autowired UserRepository users;
    @Autowired ProjectRepositoryRepository repositories;
    @Autowired ScanRepository scans;
    @Autowired IncidentRepository incidents;
    @Autowired FindingRepository findings;
    @Autowired JwtService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired DashboardService dashboard;

    @Test
    void countsRespectOwnershipDateWindowAndInvalidCleanNeverAppearsAllowed() throws Exception {
        User owner = user("dash-owner", UserRole.DEVELOPER);
        User other = user("dash-other", UserRole.DEVELOPER);
        User admin = user("dash-admin", UserRole.ADMIN);
        ProjectRepository repo = repository(owner, "dashboard-one");
        ProjectRepository foreign = repository(other, "dashboard-two");
        Scan old = scan(repo, SHA_A, ScanStatus.CLEAN, true);
        scan(repo, SHA_B, ScanStatus.CLEAN, false);
        scan(foreign, SHA_A, ScanStatus.FAILED, false);
        jdbc.update("UPDATE scans SET created_at = TIMESTAMP '2026-10-10 00:00:00' WHERE id = ?", old.getId());

        dashboard.overview(owner, null, null);
        JsonNode own = overview(owner, "");
        assertEquals(1, own.get("repositories").asInt());
        assertEquals(2, own.get("scans").asInt());
        assertEquals(0, own.get("findings").asInt());
        assertEquals(0, own.get("incidents").asInt());
        assertEquals(2, own.get("scanOutcomes").get("CLEAN").asInt());
        assertEquals(0, own.get("scanOutcomes").get("FAILED").asInt());
        assertEquals(0, own.get("findingsBySeverity").get("HIGH").asInt());
        assertEquals("BLOCK", own.get("repositorySecurity").get(0).get("gateOutcome").asText());
        assertEquals("SCAN_FAILED", own.get("repositorySecurity").get(0).get("gateReason").asText());

        JsonNode window = overview(owner, "?from=2026-10-15T00:00:00Z&to=2026-10-18T00:00:00Z");
        assertEquals(1, window.get("scans").asInt());
        assertEquals(1, window.get("scanOutcomes").get("CLEAN").asInt());
        assertEquals(1, window.get("repositories").asInt());

        JsonNode all = overview(admin, "");
        assertEquals(2, all.get("repositories").asInt());
        assertEquals(3, all.get("scans").asInt());
        assertEquals(1, all.get("scanOutcomes").get("FAILED").asInt());
        mvc.perform(get("/api/dashboard/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard/overview?from=bad")
                .header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/overview?from=2026-10-18T00:00:00Z&to=2026-10-17T00:00:00Z")
                .header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
    }

    @Test
    void findingSeveritiesAndIncidentStatesAreScopedToTheViewer() throws Exception {
        User owner = user("dash-findings-owner", UserRole.DEVELOPER);
        User other = user("dash-findings-other", UserRole.DEVELOPER);
        User admin = user("dash-findings-admin", UserRole.ADMIN);
        ProjectRepository repo = repository(owner, "findings-one");
        ProjectRepository foreign = repository(other, "findings-two");
        occurrence(repo, SHA_A, "a".repeat(64), Severity.HIGH, IncidentStatus.OPEN);
        occurrence(foreign, SHA_B, "b".repeat(64), Severity.CRITICAL, IncidentStatus.IGNORED);

        JsonNode own = overview(owner, "");
        assertEquals(1, own.get("findings").asInt());
        assertEquals(1, own.get("incidents").asInt());
        assertEquals(1, own.get("findingsBySeverity").get("HIGH").asInt());
        assertEquals(0, own.get("findingsBySeverity").get("CRITICAL").asInt());
        assertEquals(1, own.get("incidentsByStatus").get("OPEN").asInt());
        assertEquals(0, own.get("incidentsByStatus").get("IGNORED").asInt());
        assertEquals("FINDINGS_DETECTED", own.get("repositorySecurity").get(0).get("gateReason").asText());

        JsonNode all = overview(admin, "");
        assertEquals(2, all.get("findings").asInt());
        assertEquals(1, all.get("findingsBySeverity").get("CRITICAL").asInt());
        assertEquals(1, all.get("incidentsByStatus").get("IGNORED").asInt());
    }

    private JsonNode overview(User user, String suffix) throws Exception {
        var response = mvc.perform(get("/api/dashboard/overview" + suffix)
                .header("Authorization", bearer(user))).andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString());
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user.getEmail(), user.getRole().name());
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

    private Scan scan(ProjectRepository repository, String sha, ScanStatus status, boolean valid) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return scans.saveAndFlush(Scan.builder().repository(repository).commitSha(sha)
                .branch("main").trigger(ScanTrigger.MANUAL).status(status)
                .scannerVersion(valid ? "8.30.0" : null).startedAt(now)
                .completedAt(now).failureCode(status == ScanStatus.FAILED ? "SCANNER_TIMEOUT" : null)
                .build());
    }

    private void occurrence(ProjectRepository repo, String sha, String fingerprint,
            Severity severity, IncidentStatus status) {
        Scan scan = scan(repo, sha, ScanStatus.FINDINGS_DETECTED, true);
        Incident incident = incidents.saveAndFlush(Incident.builder().repository(repo)
                .fingerprint(fingerprint).severity(severity).status(status)
                .lastActorType(AuditActorType.SYSTEM).build());
        findings.saveAndFlush(Finding.builder().scan(scan).incident(incident)
                .fingerprint(fingerprint).ruleId("synthetic-rule").severity(severity)
                .filePath("config/demo.txt").line(1).commitSha(sha)
                .sanitizedDescription("Synthetic finding metadata").build());
    }
}
