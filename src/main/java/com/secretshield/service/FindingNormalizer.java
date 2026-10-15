package com.secretshield.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.secretshield.dto.SanitizedFindingInput;
import com.secretshield.entity.ProjectRepository;
import com.secretshield.entity.Scan;
import com.secretshield.entity.Severity;

import jakarta.validation.ConstraintViolationException;

@Component
public class FindingNormalizer {
    private final FindingSeverityPolicy severityPolicy;

    public FindingNormalizer(FindingSeverityPolicy severityPolicy) {
        this.severityPolicy = severityPolicy;
    }

    public NormalizedFinding normalize(Scan scan, SanitizedFindingInput input) {
        if (scan == null || scan.getId() == null || scan.getRepository() == null
                || scan.getRepository().getId() == null || input == null) {
            throw invalid();
        }
        String ruleId = input.ruleId() == null ? null : input.ruleId().strip();
        String filePath = input.filePath() == null ? null : input.filePath().strip();
        if (ruleId == null || !ruleId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")) {
            throw invalid();
        }
        if (!safeRelativePath(filePath) || input.line() < 1) {
            throw invalid();
        }
        Severity severity = severityPolicy.severityFor(ruleId);
        String fingerprint = fingerprint(scan.getRepository(), ruleId, filePath, input.line());
        String description = "Potential secret detected by rule " + ruleId;
        return new NormalizedFinding(fingerprint, ruleId, severity, filePath, input.line(), description);
    }

    public String fingerprint(ProjectRepository repository, String ruleId, String filePath, int line) {
        if (repository == null || repository.getId() == null) {
            throw invalid();
        }
        String source = repository.getId() + "\n" + ruleId + "\n" + filePath + "\n" + line;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8))).toLowerCase();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compute finding identity");
        }
    }

    private static boolean safeRelativePath(String value) {
        if (value == null || value.isBlank() || value.length() > 1000
                || value.startsWith("/") || value.startsWith("\\")
                || value.contains("\\") || value.matches("^[A-Za-z]:.*")
                || value.chars().anyMatch(character -> Character.isISOControl(character))) {
            return false;
        }
        List<String> segments = List.of(value.split("/", -1));
        return segments.stream().noneMatch(segment -> segment.isBlank()
                || segment.equals(".") || segment.equals(".."));
    }

    private static ConstraintViolationException invalid() {
        return new ConstraintViolationException("Invalid finding metadata", Set.of());
    }

    public record NormalizedFinding(
            String fingerprint,
            String ruleId,
            Severity severity,
            String filePath,
            int line,
            String sanitizedDescription) {}
}
