package com.secretshield.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

class GitleaksScannerAdapterTests {
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @TempDir
    Path temporaryDirectory;

    @Test
    void normalizesOnlySafeFieldsAndDropsRawSecretMaterial() {
        String rawMarker = "value-that-must-not-survive-normalization";
        String report = """
                [{
                  "RuleID":"secret-shield-synthetic",
                  "File":"folder\\\\settings.txt",
                  "StartLine":7,
                  "Commit":"%s",
                  "Secret":"%s",
                  "Match":"verification_marker=%s"
                }]
                """.formatted(COMMIT, rawMarker, rawMarker);

        var findings = adapter(10_000).normalizeReport(
                report.getBytes(StandardCharsets.UTF_8), temporaryDirectory, COMMIT);

        assertEquals(1, findings.size());
        assertEquals("secret-shield-synthetic", findings.getFirst().ruleId());
        assertEquals("folder/settings.txt", findings.getFirst().filePath());
        assertEquals(7, findings.getFirst().line());
        assertFalse(findings.toString().contains(rawMarker));
    }

    @Test
    void rejectsMalformedReportAndWrongCommitOrEscapingPath() {
        assertCode("SCANNER_REPORT_INVALID", () -> adapter(100).normalizeReport(
                "not-json".getBytes(StandardCharsets.UTF_8), temporaryDirectory, COMMIT));
        assertCode("SCANNER_REPORT_INVALID", () -> adapter(1_000).normalizeReport(
                item("../outside.txt", COMMIT)
                        .getBytes(StandardCharsets.UTF_8), temporaryDirectory, COMMIT));
        assertCode("SCANNER_REPORT_INVALID", () -> adapter(1_000).normalizeReport(
                item("safe.txt", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                        .getBytes(StandardCharsets.UTF_8), temporaryDirectory, COMMIT));
    }

    @Test
    void rejectsOversizedReportBeforeParsing() {
        assertCode("SCANNER_REPORT_TOO_LARGE", () -> adapter(2).normalizeReport(
                "[] ".getBytes(StandardCharsets.UTF_8), temporaryDirectory, COMMIT));
    }

    private GitleaksScannerAdapter adapter(long maxReportBytes) {
        return new GitleaksScannerAdapter(
                temporaryDirectory.toString(), "missing-gitleaks", "config/gitleaks.toml",
                "8.30.0", 1, maxReportBytes, 1_024, JsonMapper.builder().build());
    }

    private static String item(String path, String commit) {
        return "[{\"RuleID\":\"rule\",\"File\":\"" + path
                + "\",\"StartLine\":1,\"Commit\":\"" + commit + "\"}]";
    }

    private static void assertCode(String expected, Runnable operation) {
        ScannerFailureException exception = assertThrows(ScannerFailureException.class, operation::run);
        assertEquals(expected, exception.code());
    }
}
