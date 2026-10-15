package com.secretshield.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.dto.SanitizedFindingInput;
import com.secretshield.entity.Finding;
import com.secretshield.entity.Scan;
import com.secretshield.repository.FindingRepository;

@Service
public class FindingPersistenceService implements FindingResultSink {
    private final FindingRepository findings;
    private final FindingNormalizer normalizer;

    public FindingPersistenceService(FindingRepository findings, FindingNormalizer normalizer) {
        this.findings = findings;
        this.normalizer = normalizer;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void persist(Scan scan, List<SanitizedFindingInput> inputs) {
        if (inputs == null) {
            throw new jakarta.validation.ConstraintViolationException("Invalid finding metadata", java.util.Set.of());
        }
        var normalizedByFingerprint = new LinkedHashMap<String, FindingNormalizer.NormalizedFinding>();
        for (var input : inputs) {
            var normalized = normalizer.normalize(scan, input);
            normalizedByFingerprint.putIfAbsent(normalized.fingerprint(), normalized);
        }
        var existing = new HashSet<>(findings.findFingerprintsByScanId(scan.getId()));
        var newFindings = new ArrayList<Finding>();
        for (var normalized : normalizedByFingerprint.values()) {
            if (existing.add(normalized.fingerprint())) {
                newFindings.add(Finding.builder()
                        .scan(scan)
                        .fingerprint(normalized.fingerprint())
                        .ruleId(normalized.ruleId())
                        .severity(normalized.severity())
                        .filePath(normalized.filePath())
                        .line(normalized.line())
                        .commitSha(scan.getCommitSha())
                        .sanitizedDescription(normalized.sanitizedDescription())
                        .build());
            }
        }
        if (!newFindings.isEmpty()) {
            findings.saveAllAndFlush(newFindings);
        }
    }
}
