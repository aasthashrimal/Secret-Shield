package com.secretshield.scanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.secretshield.dto.SanitizedFindingInput;
import com.secretshield.entity.ProjectRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GitleaksScannerAdapter implements LocalScanner {
    private static final int MAX_FINDINGS = 1_000;
    private static final long PROCESS_GRACE_SECONDS = 1;

    private final Path workspace;
    private final Path configPath;
    private final String executable;
    private final String expectedVersion;
    private final int timeoutSeconds;
    private final long maxReportBytes;
    private final long maxLogBytes;
    private final JsonMapper json;

    public GitleaksScannerAdapter(
            @Value("${secretshield.scanner.workspace}") String workspace,
            @Value("${secretshield.scanner.gitleaks-path}") String executable,
            @Value("${secretshield.scanner.config-path}") String configPath,
            @Value("${secretshield.scanner.version}") String expectedVersion,
            @Value("${secretshield.scanner.timeout-seconds}") int timeoutSeconds,
            @Value("${secretshield.scanner.max-report-bytes}") long maxReportBytes,
            @Value("${secretshield.scanner.max-log-bytes}") long maxLogBytes,
            JsonMapper json) {
        this.workspace = Path.of(workspace).toAbsolutePath().normalize();
        this.configPath = Path.of(configPath).toAbsolutePath().normalize();
        this.executable = executable;
        this.expectedVersion = expectedVersion;
        this.timeoutSeconds = timeoutSeconds;
        this.maxReportBytes = maxReportBytes;
        this.maxLogBytes = maxLogBytes;
        this.json = json;
    }

    @Override
    public Result scan(ProjectRepository repository, String commit, String branch) {
        Path source = resolveSource(repository);
        verifyGitIdentity(source, commit, branch);
        verifyConfiguration();
        String version = readVersion();
        Path temporaryDirectory = null;
        try {
            temporaryDirectory = Files.createTempDirectory("secret-shield-gitleaks-");
            Path report = temporaryDirectory.resolve("report.json");
            Path stdout = temporaryDirectory.resolve("stdout.log");
            Path stderr = temporaryDirectory.resolve("stderr.log");
            List<String> arguments = List.of(
                    executable, "git", source.toString(),
                    "--config=" + configPath,
                    "--log-opts=-1 " + commit,
                    "--report-format=json",
                    "--report-path=" + report,
                    "--redact=100",
                    "--no-banner",
                    "--no-color",
                    "--log-level=error",
                    "--exit-code=1",
                    "--timeout=" + timeoutSeconds);

            Process process = new ProcessBuilder(arguments)
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile())
                    .start();
            int exitCode = await(process, report, stdout, stderr);
            List<SanitizedFindingInput> findings = parseAndDeleteReport(report, source, commit);
            if (exitCode == 0 && findings.isEmpty()) {
                return new Result(version, List.of());
            }
            if (exitCode == 1 && !findings.isEmpty()) {
                return new Result(version, findings);
            }
            throw failure("SCANNER_PROCESS_ERROR");
        } catch (ScannerFailureException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("SCANNER_INTERRUPTED");
        } catch (IOException exception) {
            throw failure("SCANNER_IO_ERROR");
        } finally {
            cleanup(temporaryDirectory);
        }
    }

    private int await(Process process, Path report, Path stdout, Path stderr) throws InterruptedException {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(timeoutSeconds + PROCESS_GRACE_SECONDS);
        while (process.isAlive()) {
            if (System.nanoTime() > deadline) {
                terminate(process);
                throw failure("SCANNER_TIMEOUT");
            }
            if (size(report) > maxReportBytes || size(stdout) > maxLogBytes || size(stderr) > maxLogBytes) {
                terminate(process);
                throw failure("SCANNER_OUTPUT_LIMIT");
            }
            Thread.sleep(25);
        }
        if (size(report) > maxReportBytes) {
            throw failure("SCANNER_REPORT_TOO_LARGE");
        }
        if (size(stdout) > maxLogBytes || size(stderr) > maxLogBytes) {
            throw failure("SCANNER_OUTPUT_LIMIT");
        }
        return process.exitValue();
    }

    private static void terminate(Process process) throws InterruptedException {
        process.destroyForcibly();
        process.waitFor(PROCESS_GRACE_SECONDS, TimeUnit.SECONDS);
    }

    private Path resolveSource(ProjectRepository repository) {
        try {
            Path root = workspace.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path candidate = root.resolve(repository.getGithubOwner())
                    .resolve(repository.getGithubName()).normalize();
            if (!candidate.startsWith(root) || Files.isSymbolicLink(candidate)) {
                throw failure("SCANNER_SOURCE_INVALID");
            }
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw failure("SCANNER_SOURCE_MISSING");
            }
            if (!Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw failure("SCANNER_SOURCE_INVALID");
            }
            Path real = candidate.toRealPath();
            if (!real.startsWith(root)) {
                throw failure("SCANNER_SOURCE_INVALID");
            }
            return real;
        } catch (ScannerFailureException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure("SCANNER_SOURCE_MISSING");
        }
    }

    private void verifyConfiguration() {
        if (!Files.isRegularFile(configPath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(configPath)) {
            throw failure("SCANNER_CONFIG_INVALID");
        }
    }

    private void verifyGitIdentity(Path source, String commit, String branch) {
        String head = runGit(source, "rev-parse", "HEAD");
        String currentBranch = runGit(source, "branch", "--show-current");
        if (!head.equals(commit) || !currentBranch.equals(branch)) {
            throw failure("SCANNER_IDENTITY_MISMATCH");
        }
    }

    private String runGit(Path source, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(source.toString());
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                terminate(process);
                throw failure("SCANNER_GIT_ERROR");
            }
            byte[] output = process.getInputStream()
                    .readNBytes((int) Math.min(maxLogBytes + 1, Integer.MAX_VALUE));
            if (process.exitValue() != 0 || output.length > maxLogBytes) {
                throw failure("SCANNER_GIT_ERROR");
            }
            return new String(output, StandardCharsets.UTF_8).strip();
        } catch (ScannerFailureException exception) {
            throw exception;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw failure("SCANNER_GIT_ERROR");
        }
    }

    private String readVersion() {
        try {
            Process process = new ProcessBuilder(executable, "version").redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                terminate(process);
                throw failure("SCANNER_MISSING");
            }
            byte[] output = process.getInputStream().readNBytes(4_097);
            String text = new String(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0 || output.length > 4_096
                    || !matchesVersion(text, expectedVersion)) {
                throw failure("SCANNER_VERSION_MISMATCH");
            }
            return expectedVersion;
        } catch (ScannerFailureException exception) {
            throw exception;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw failure("SCANNER_MISSING");
        }
    }

    static boolean matchesVersion(String output, String expectedVersion) {
        return output.matches("(?s).*\\bv?" + Pattern.quote(expectedVersion) + "(?![\\w.]).*");
    }

    private List<SanitizedFindingInput> parseAndDeleteReport(Path report, Path source, String commit) {
        if (!Files.exists(report)) {
            return List.of();
        }
        try {
            if (Files.size(report) > maxReportBytes) {
                throw failure("SCANNER_REPORT_TOO_LARGE");
            }
            byte[] bytes = Files.readAllBytes(report);
            Files.deleteIfExists(report);
            return normalizeReport(bytes, source, commit);
        } catch (ScannerFailureException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure("SCANNER_REPORT_INVALID");
        }
    }

    List<SanitizedFindingInput> normalizeReport(byte[] report, Path source, String expectedCommit) {
        try {
            if (report.length > maxReportBytes) {
                throw failure("SCANNER_REPORT_TOO_LARGE");
            }
            JsonNode root = json.readTree(report);
            if (root == null || !root.isArray() || root.size() > MAX_FINDINGS) {
                throw failure("SCANNER_REPORT_INVALID");
            }
            List<SanitizedFindingInput> sanitized = new ArrayList<>();
            for (JsonNode item : root) {
                String rule = requiredText(item, "RuleID", 200);
                String file = requiredText(item, "File", 4_000);
                String reportCommit = requiredText(item, "Commit", 40);
                int line = item.path("StartLine").asInt(0);
                if (!reportCommit.equals(expectedCommit)) {
                    throw failure("SCANNER_REPORT_INVALID");
                }
                Path path = Path.of(file);
                if (path.isAbsolute()) {
                    Path normalizedAbsolute = path.normalize();
                    if (!normalizedAbsolute.startsWith(source)) {
                        throw failure("SCANNER_REPORT_INVALID");
                    }
                    path = source.relativize(normalizedAbsolute);
                }
                String normalized = path.normalize().toString().replace('\\', '/');
                if (normalized.isBlank() || normalized.startsWith("/")
                        || Arrays.asList(normalized.split("/")).contains("..") || line < 1) {
                    throw failure("SCANNER_REPORT_INVALID");
                }
                sanitized.add(new SanitizedFindingInput(
                        rule, normalized, line, "Potential secret detected by rule " + rule));
            }
            return List.copyOf(sanitized);
        } catch (ScannerFailureException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("SCANNER_REPORT_INVALID");
        }
    }

    private static String requiredText(JsonNode item, String field, int maxLength) {
        String value = item.path(field).asText("");
        if (value.isBlank() || value.length() > maxLength) {
            throw failure("SCANNER_REPORT_INVALID");
        }
        return value;
    }

    private static long size(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0;
        } catch (IOException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static ScannerFailureException failure(String code) {
        return new ScannerFailureException(code);
    }

    private static void cleanup(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A later OS cleanup can remove a file still held by a terminated process.
                }
            });
        } catch (IOException ignored) {
            // Scanner results never expose temporary paths or cleanup details.
        }
    }
}
