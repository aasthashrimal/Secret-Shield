package com.secretshield.dto;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.secretshield.entity.Finding;

public record FindingDto(
        String id,
        String scanId,
        String incidentId,
        String fingerprint,
        String ruleId,
        String severity,
        String filePath,
        int line,
        String commitSha,
        String sanitizedDescription,
        String createdAt) {

    public static FindingDto from(Finding finding) {
        return new FindingDto(
                finding.getId().toString(),
                finding.getScan().getId().toString(),
                null,
                finding.getFingerprint(),
                finding.getRuleId(),
                finding.getSeverity().name(),
                finding.getFilePath(),
                finding.getLine(),
                finding.getCommitSha(),
                finding.getSanitizedDescription(),
                utc(finding.getCreatedAt()));
    }

    private static String utc(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toString();
    }
}
