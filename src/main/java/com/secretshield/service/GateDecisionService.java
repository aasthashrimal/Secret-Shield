package com.secretshield.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.dto.GateDto;
import com.secretshield.entity.Scan;
import com.secretshield.entity.ScanStatus;
import com.secretshield.entity.ScanTrigger;
import com.secretshield.entity.User;
import com.secretshield.repository.FindingRepository;
import com.secretshield.repository.ScanRepository;

@Service
public class GateDecisionService {
    private final ProjectRepositoryService repositories;
    private final ScanRepository scans;
    private final FindingRepository findings;
    private final String scannerVersion;

    public GateDecisionService(ProjectRepositoryService repositories, ScanRepository scans,
            FindingRepository findings,
            @Value("${secretshield.scanner.version}") String scannerVersion) {
        this.repositories = repositories;
        this.scans = scans;
        this.findings = findings;
        this.scannerVersion = scannerVersion;
    }

    @Transactional(readOnly = true)
    public GateDto decide(User actor, long repositoryId, String commitSha) {
        var repository = repositories.loadAuthorized(actor, repositoryId);
        String sha = ScanService.sha(commitSha);
        Scan scan = scans.findFirstByRepositoryIdAndCommitShaOrderByCreatedAtDescIdDesc(
                repository.getId(), sha).orElse(null);
        if (scan == null) {
            return new GateDto(repository.getId().toString(), sha, null, "BLOCK", "NO_SCAN");
        }
        String reason = switch (scan.getStatus()) {
            case QUEUED, RUNNING -> "SCAN_PENDING";
            case FINDINGS_DETECTED -> "FINDINGS_DETECTED";
            case FAILED -> "SCAN_FAILED";
            case CLEAN -> validClean(scan) ? "CLEAN" : "SCAN_FAILED";
        };
        String outcome = reason.equals("CLEAN") ? "ALLOW" : "BLOCK";
        return new GateDto(repository.getId().toString(), sha, scan.getId().toString(),
                outcome, reason);
    }

    private boolean validClean(Scan scan) {
        if (scan.getStartedAt() == null || scan.getCompletedAt() == null
                || scan.getCompletedAt().isBefore(scan.getStartedAt())
                || !scannerVersion.equals(scan.getScannerVersion())
                || scan.getFailureCode() != null || findings.countByScanId(scan.getId()) != 0) {
            return false;
        }
        if (scan.getTrigger() == ScanTrigger.MANUAL) {
            return scan.getRunId() == null && scan.getRunAttempt() == null;
        }
        return scan.getRunId() != null && scan.getRunAttempt() != null
                && scan.getRunAttempt() > 0 && scan.getPayloadDigest() != null
                && scan.getPayloadDigest().matches("[0-9a-f]{64}");
    }
}
