package com.secretshield.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.dto.ScanDto;
import com.secretshield.entity.Scan;
import com.secretshield.entity.AuditOperation;
import com.secretshield.entity.AuditTargetType;
import com.secretshield.entity.ScanStatus;
import com.secretshield.entity.ScanTrigger;
import com.secretshield.entity.User;
import com.secretshield.exception.ConflictException;
import com.secretshield.exception.ResourceNotFoundException;
import com.secretshield.repository.ScanRepository;
import com.secretshield.scanner.LocalScanner;
import com.secretshield.scanner.ScannerFailureException;
import com.secretshield.security.ResourceAuthorization;

@Service
public class LocalScanExecutionService {
    private final ScanRepository scans;
    private final ProjectRepositoryService repositories;
    private final ResourceAuthorization authorization;
    private final LocalScanner scanner;
    private final List<FindingResultSink> sinks;
    private final String scannerVersion;
    private final AuditService audit;

    public LocalScanExecutionService(
            ScanRepository scans,
            ProjectRepositoryService repositories,
            ResourceAuthorization authorization,
            LocalScanner scanner,
            List<FindingResultSink> sinks,
            AuditService audit,
            @Value("${secretshield.scanner.version}") String scannerVersion) {
        this.scans = scans;
        this.repositories = repositories;
        this.authorization = authorization;
        this.scanner = scanner;
        this.sinks = sinks;
        this.audit = audit;
        this.scannerVersion = scannerVersion;
    }

    @Transactional
    public ScanDto execute(User actor, long repositoryId, long scanId) {
        Scan claimed = claim(actor, repositoryId, scanId);
        LocalScanner.Result result;
        try {
            result = scanner.scan(claimed.getRepository(), claimed.getCommitSha(), claimed.getBranch());
            if (result == null || !scannerVersion.equals(result.version()) || result.findings() == null) {
                throw new ScannerFailureException("SCANNER_REPORT_INVALID");
            }
        } catch (ScannerFailureException exception) {
            return fail(scanId, exception.code());
        } catch (Exception exception) {
            return fail(scanId, "SCANNER_INTERNAL_ERROR");
        }
        // A persistence/sink failure propagates so the transaction restores QUEUED
        // rather than committing a terminal scan without all normalized results.
        return finish(scanId, result);
    }

    private Scan claim(User actor, long repositoryId, long scanId) {
        var repository = repositories.loadActiveAuthorized(actor, repositoryId);
        var scan = scans.findLockedById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found"));
        authorization.requireNestedResource(actor, repository.getOwner().getId(),
                repositoryId, scan.getRepository().getId());
        if (scan.getTrigger() != ScanTrigger.MANUAL || scan.getStatus() != ScanStatus.QUEUED) {
            throw new ConflictException("Scan cannot be started");
        }
        ScanService.transition(scan, ScanStatus.RUNNING, null, null);
        scans.saveAndFlush(scan);
        audit.user(repository, actor, AuditOperation.SCAN_STARTED, AuditTargetType.SCAN,
                scan.getId(), "Manual scan started");
        return scan;
    }

    private ScanDto finish(long scanId, LocalScanner.Result result) {
        var scan = scans.findLockedById(scanId).orElseThrow();
        if (scan.getStatus() != ScanStatus.RUNNING) {
            throw new ConflictException("Scan state changed");
        }
        ScanStatus status = result.findings().isEmpty()
                ? ScanStatus.CLEAN : ScanStatus.FINDINGS_DETECTED;
        ScanService.transition(scan, status, result.version(), null);
        scans.saveAndFlush(scan);
        audit.system(scan.getRepository(), AuditOperation.SCAN_COMPLETED, AuditTargetType.SCAN,
                scan.getId(), "Scan completed with status " + status.name());
        for (var sink : sinks) {
            sink.persist(scan, result.findings());
        }
        return ScanDto.from(scan);
    }

    private ScanDto fail(long scanId, String code) {
        var scan = scans.findLockedById(scanId).orElseThrow();
        if (scan.getStatus() == ScanStatus.RUNNING) {
            ScanService.transition(scan, ScanStatus.FAILED, scannerVersion, ScanService.failure(code));
            scans.saveAndFlush(scan);
            audit.system(scan.getRepository(), AuditOperation.SCAN_FAILED, AuditTargetType.SCAN,
                    scan.getId(), "Scan failed");
        }
        return ScanDto.from(scan);
    }
}
