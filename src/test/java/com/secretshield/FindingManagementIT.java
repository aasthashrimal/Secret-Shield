package com.secretshield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.secretshield.entity.ProjectRepository;
import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;
import com.secretshield.repository.FindingRepository;
import com.secretshield.repository.ProjectRepositoryRepository;
import com.secretshield.repository.ScanRepository;
import com.secretshield.repository.UserRepository;
import com.secretshield.security.JwtService;
import com.secretshield.support.PostgresApiTest;

class FindingManagementIT extends PostgresApiTest {
    @Autowired UserRepository users;
    @Autowired ProjectRepositoryRepository repositories;
    @Autowired ScanRepository scans;
    @Autowired FindingRepository findings;
    @Autowired JwtService tokens;

    @Test
    void ingestionDeduplicatesWithinScanButRetainsLaterOccurrences() throws Exception {
        User owner = user("finding-owner", UserRole.DEVELOPER);
        ProjectRepository repository = repository(owner, "occurrences");
        String credential = issue(owner, repository);
        String sensitiveDescription = "raw-value-that-must-never-be-returned";
        Map<String, Object> input = finding("synthetic-low", "config/demo.txt", 2,
                sensitiveDescription);
        Map<String, Object> firstBody = result(1, List.of(input, input));

        var firstResponse = submit(repository, credential, firstBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FINDINGS_DETECTED"))
                .andReturn();
        String firstScanId = json.readTree(firstResponse.getResponse().getContentAsString())
                .get("id").asText();
        assertEquals(1, findings.count());

        var listed = mvc.perform(get("/api/repositories/{repository}/scans/{scan}/findings"
                        + "?severity=LOW&ruleId=synthetic-low&from=2020-01-01T00:00:00Z"
                        + "&to=2100-01-01T00:00:00Z&size=1",
                        repository.getId(), firstScanId)
                .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].severity").value("LOW"))
                .andExpect(jsonPath("$.content[0].filePath").value("config/demo.txt"))
                .andExpect(jsonPath("$.content[0].line").value(2))
                .andExpect(jsonPath("$.content[0].incidentId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].sanitizedDescription")
                        .value("Potential secret detected by rule synthetic-low"))
                .andReturn();
        String body = listed.getResponse().getContentAsString();
        assertFalse(body.contains(sensitiveDescription));
        String findingId = json.readTree(body).get("content").get(0).get("id").asText();
        String firstFingerprint = json.readTree(body).get("content").get(0)
                .get("fingerprint").asText();

        mvc.perform(get("/api/repositories/{repository}/findings/{finding}",
                        repository.getId(), findingId)
                .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fingerprint").value(firstFingerprint));

        submit(repository, credential, firstBody).andExpect(status().isOk());
        assertEquals(1, findings.count());

        var secondResponse = submit(repository, credential, result(2, List.of(input)))
                .andExpect(status().isCreated()).andReturn();
        String secondScanId = json.readTree(secondResponse.getResponse().getContentAsString())
                .get("id").asText();
        var secondList = mvc.perform(get("/api/repositories/{repository}/scans/{scan}/findings",
                        repository.getId(), secondScanId)
                .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();
        assertEquals(firstFingerprint, json.readTree(secondList.getResponse().getContentAsString())
                .get("content").get(0).get("fingerprint").asText());
        assertEquals(2, findings.count());
    }

    @Test
    void unknownRuleUsesHighFallbackAndInvalidMetadataRollsBack() throws Exception {
        User owner = user("fallback-owner", UserRole.DEVELOPER);
        ProjectRepository repository = repository(owner, "fallback");
        String credential = issue(owner, repository);
        var response = submit(repository, credential, result(1, List.of(
                finding("future-provider-rule", "src/config.txt", 5, "caller supplied text"))))
                .andExpect(status().isCreated()).andReturn();
        String scanId = json.readTree(response.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/repositories/{repository}/scans/{scan}/findings?severity=HIGH",
                        repository.getId(), scanId)
                .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"));

        long scansBefore = scans.count();
        long findingsBefore = findings.count();
        submit(repository, credential, result(2, List.of(
                finding("bad rule", "../outside.txt", 1, "unsafe"))))
                .andExpect(status().isBadRequest());
        assertEquals(scansBefore, scans.count());
        assertEquals(findingsBefore, findings.count());
    }

    @Test
    void ownershipNestedIdentityAndUnknownFieldsAreEnforced() throws Exception {
        User owner = user("finding-a", UserRole.DEVELOPER);
        User other = user("finding-b", UserRole.DEVELOPER);
        User admin = user("finding-admin", UserRole.ADMIN);
        ProjectRepository first = repository(owner, "owned");
        ProjectRepository second = repository(other, "other");
        String credential = issue(owner, first);
        var response = submit(first, credential, result(1, List.of(
                finding("synthetic-info", "safe.txt", 1, "safe"))))
                .andExpect(status().isCreated()).andReturn();
        String scanId = json.readTree(response.getResponse().getContentAsString()).get("id").asText();
        var list = mvc.perform(get("/api/repositories/{repository}/scans/{scan}/findings",
                        first.getId(), scanId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn();
        String findingId = json.readTree(list.getResponse().getContentAsString())
                .get("content").get(0).get("id").asText();

        mvc.perform(get("/api/repositories/{repository}/findings/{finding}",
                        first.getId(), findingId).header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/repositories/{repository}/findings/{finding}",
                        second.getId(), findingId).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/repositories/{repository}/findings/{finding}",
                        first.getId(), findingId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        Map<String, Object> withRawField = new HashMap<>(finding(
                "synthetic-info", "safe.txt", 1, "safe"));
        withRawField.put("secret", "must-not-be-accepted");
        submit(first, credential, result(2, List.of(withRawField)))
                .andExpect(status().isBadRequest());
        assertEquals(1, findings.count());
    }

    private User user(String name, UserRole role) {
        return users.saveAndFlush(User.builder().username(name).email(name + "@example.invalid")
                .password("hash").role(role).build());
    }

    private ProjectRepository repository(User owner, String name) {
        return repositories.saveAndFlush(ProjectRepository.builder().owner(owner)
                .githubOwner("demo").githubName(name)
                .canonicalUrl("https://github.com/demo/" + name)
                .defaultBranch("main").active(true).build());
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user.getEmail(), user.getRole().name());
    }

    private String issue(User user, ProjectRepository repository) throws Exception {
        var response = mvc.perform(post("/api/repositories/{id}/ci-credentials", repository.getId())
                        .header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("credential").asText();
    }

    private org.springframework.test.web.servlet.ResultActions submit(
            ProjectRepository repository, String credential, Object body) throws Exception {
        return mvc.perform(post("/api/ci/repositories/{id}/scan-results", repository.getId())
                .header("X-Secret-Shield-Credential", credential)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    private static Map<String, Object> result(int attempt, List<?> findingList) {
        var result = new HashMap<String, Object>();
        result.put("runId", "finding-run");
        result.put("runAttempt", attempt);
        result.put("commitSha", "0123456789abcdef0123456789abcdef01234567");
        result.put("branch", "main");
        result.put("trigger", "CI_PUSH");
        result.put("scannerVersion", "8.30.0");
        result.put("status", "FINDINGS_DETECTED");
        result.put("findings", findingList);
        return result;
    }

    private static Map<String, Object> finding(
            String ruleId, String filePath, int line, String description) {
        return Map.of("ruleId", ruleId, "filePath", filePath, "line", line,
                "sanitizedDescription", description);
    }
}
