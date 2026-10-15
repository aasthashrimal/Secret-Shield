package com.secretshield.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.secretshield.entity.ProjectRepository;

import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "GITLEAKS_REAL_TEST", matches = "true")
class GitleaksRealFixtureTests {
    private static final Path WORKSPACE = Path.of("target/scanner-workspace").toAbsolutePath();

    @Test
    void pinnedScannerDistinguishesCleanAndFindingFixtures() throws Exception {
        var scanner = new GitleaksScannerAdapter(
                WORKSPACE.toString(), requiredEnvironment("GITLEAKS_PATH"),
                Path.of("config/gitleaks.toml").toAbsolutePath().toString(),
                "8.30.0", 10, 5_242_880, 65_536, JsonMapper.builder().build());

        Path clean = WORKSPACE.resolve("fixture-owner/clean-fixture");
        var cleanResult = scanner.scan(repository("clean-fixture"), head(clean), "main");
        assertEquals("8.30.0", cleanResult.version());
        assertTrue(cleanResult.findings().isEmpty());

        Path finding = WORKSPACE.resolve("fixture-owner/finding-fixture");
        var findingResult = scanner.scan(repository("finding-fixture"), head(finding), "main");
        assertEquals(1, findingResult.findings().size());
        assertEquals("secret-shield-synthetic", findingResult.findings().getFirst().ruleId());
        assertEquals("fixture.txt", findingResult.findings().getFirst().filePath());
        assertFalse(findingResult.toString().contains("SECRET_SHIELD_SYNTHETIC_"));
    }

    @Test
    void missingAndWrongScannerVersionsAreControlled() throws Exception {
        Path clean = WORKSPACE.resolve("fixture-owner/clean-fixture");
        String commit = head(clean);
        var missing = scanner(WORKSPACE.resolve("missing-gitleaks.exe").toString(), "8.30.0", 10);
        assertCode("SCANNER_MISSING", () -> missing.scan(repository("clean-fixture"), commit, "main"));

        var wrongVersion = scanner(requiredEnvironment("GITLEAKS_PATH"), "0.0.0", 10);
        assertCode("SCANNER_VERSION_MISMATCH",
                () -> wrongVersion.scan(repository("clean-fixture"), commit, "main"));
    }

    @Test
    void externalProcessTimeoutIsControlled() throws Exception {
        Path command = Path.of("target/scanner-timeout.cmd").toAbsolutePath();
        Files.writeString(command, """
                @echo off
                if "%1"=="version" (
                  echo 8.30.0
                  exit /b 0
                )
                ping 127.0.0.1 -n 8 > nul
                exit /b 0
                """);
        try {
            Path clean = WORKSPACE.resolve("fixture-owner/clean-fixture");
            var timeout = scanner(command.toString(), "8.30.0", 1);
            assertCode("SCANNER_TIMEOUT",
                    () -> timeout.scan(repository("clean-fixture"), headUnchecked(clean), "main"));
        } finally {
            Files.deleteIfExists(command);
        }
    }

    @Test
    void nonScannerExitCodeIsControlled() throws Exception {
        Path command = Path.of("target/scanner-error.cmd").toAbsolutePath();
        Files.writeString(command, """
                @echo off
                if "%1"=="version" (
                  echo 8.30.0
                  exit /b 0
                )
                exit /b 7
                """);
        try {
            Path clean = WORKSPACE.resolve("fixture-owner/clean-fixture");
            var scanner = scanner(command.toString(), "8.30.0", 2);
            assertCode("SCANNER_PROCESS_ERROR",
                    () -> scanner.scan(repository("clean-fixture"), headUnchecked(clean), "main"));
        } finally {
            Files.deleteIfExists(command);
        }
    }

    private static GitleaksScannerAdapter scanner(String executable, String version, int timeout) {
        return new GitleaksScannerAdapter(
                WORKSPACE.toString(), executable,
                Path.of("config/gitleaks.toml").toAbsolutePath().toString(),
                version, timeout, 5_242_880, 65_536, JsonMapper.builder().build());
    }

    private static ProjectRepository repository(String name) {
        return ProjectRepository.builder().githubOwner("fixture-owner").githubName(name).build();
    }

    private static String head(Path repository) throws Exception {
        Process process = new ProcessBuilder("git", "-C", repository.toString(), "rev-parse", "HEAD")
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        return new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the opt-in real scanner test");
        }
        return value;
    }

    private static String headUnchecked(Path repository) {
        try {
            return head(repository);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertCode(String expected, Runnable operation) {
        ScannerFailureException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ScannerFailureException.class, operation::run);
        assertEquals(expected, exception.code());
    }
}
