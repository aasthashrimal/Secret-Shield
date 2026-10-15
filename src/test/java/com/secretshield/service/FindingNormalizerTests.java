package com.secretshield.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.secretshield.dto.SanitizedFindingInput;
import com.secretshield.entity.ProjectRepository;
import com.secretshield.entity.Scan;
import com.secretshield.entity.Severity;

import jakarta.validation.ConstraintViolationException;

class FindingNormalizerTests {
    private final FindingSeverityPolicy severityPolicy = new FindingSeverityPolicy();
    private final FindingNormalizer normalizer = new FindingNormalizer(severityPolicy);

    @Test
    void severityMappingIncludesEveryPolicyTierAndHighFallback() {
        assertEquals(Severity.CRITICAL, severityPolicy.severityFor("private-key"));
        assertEquals(Severity.HIGH, severityPolicy.severityFor("github-pat"));
        assertEquals(Severity.MEDIUM, severityPolicy.severityFor("generic-password"));
        assertEquals(Severity.LOW, severityPolicy.severityFor("synthetic-low"));
        assertEquals(Severity.INFO, severityPolicy.severityFor("synthetic-info"));
        assertEquals(Severity.HIGH, severityPolicy.severityFor("future-rule"));
    }

    @Test
    void fingerprintMatchesVersionOneContractAndExcludesDescriptionAndCommit() {
        var repository = ProjectRepository.builder().id(42L).build();
        var firstScan = Scan.builder().id(1L).repository(repository)
                .commitSha("0123456789abcdef0123456789abcdef01234567").build();
        var secondScan = Scan.builder().id(2L).repository(repository)
                .commitSha("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").build();
        var first = normalizer.normalize(firstScan,
                input("synthetic-low", "config/demo.txt", 2, "first description"));
        var second = normalizer.normalize(secondScan,
                input("synthetic-low", "config/demo.txt", 2, "different description"));

        assertEquals("1a20b5fc9d5e2a9da4a536ff39e12af2910231fed3b7d54978308f3b9a18b01a",
                first.fingerprint());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertNotEquals(first.fingerprint(), normalizer.normalize(firstScan,
                input("synthetic-low", "config/demo.txt", 3, "different line")).fingerprint());
        assertEquals("Potential secret detected by rule synthetic-low", first.sanitizedDescription());
    }

    @Test
    void rejectsUnsafeRulesAndPaths() {
        var scan = Scan.builder().id(1L).repository(ProjectRepository.builder().id(2L).build())
                .commitSha("0123456789abcdef0123456789abcdef01234567").build();
        for (var input : new SanitizedFindingInput[] {
                input("bad rule", "safe.txt", 1, "safe"),
                input("rule", "../outside.txt", 1, "safe"),
                input("rule", "folder\\outside.txt", 1, "safe"),
                input("rule", "folder//file.txt", 1, "safe"),
                input("rule", "safe.txt", 0, "safe")}) {
            assertThrows(ConstraintViolationException.class, () -> normalizer.normalize(scan, input));
        }
    }

    private static SanitizedFindingInput input(String rule, String path, int line, String description) {
        return new SanitizedFindingInput(rule, path, line, description);
    }
}
