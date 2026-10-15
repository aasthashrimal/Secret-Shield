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
import com.secretshield.entity.Incident;
import com.secretshield.entity.IncidentStatus;
import com.secretshield.entity.AuditActorType;
import com.secretshield.entity.AuditOperation;
import com.secretshield.entity.AuditTargetType;
import com.secretshield.entity.Scan;
import com.secretshield.repository.FindingRepository;
import com.secretshield.repository.IncidentRepository;
import com.secretshield.repository.ProjectRepositoryRepository;

@Service
public class FindingPersistenceService implements FindingResultSink {
    private final FindingRepository findings;
    private final FindingNormalizer normalizer;
    private final IncidentRepository incidents;
    private final ProjectRepositoryRepository repositories;
    private final AuditService audit;

    public FindingPersistenceService(FindingRepository findings, FindingNormalizer normalizer,
            IncidentRepository incidents, ProjectRepositoryRepository repositories, AuditService audit) {
        this.findings = findings;
        this.normalizer = normalizer;
        this.incidents = incidents;
        this.repositories = repositories;
        this.audit = audit;
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
        // Serializing by repository also protects the unique incident identity across scans.
        var repository = repositories.findLockedById(scan.getRepository().getId()).orElseThrow();
        for (var normalized : normalizedByFingerprint.values()) {
            if (existing.add(normalized.fingerprint())) {
                Incident incident = incidents.findByRepositoryIdAndFingerprint(
                        repository.getId(), normalized.fingerprint()).orElse(null);
                if (incident == null) {
                    incident = incidents.saveAndFlush(Incident.builder().repository(repository)
                            .fingerprint(normalized.fingerprint()).severity(normalized.severity())
                            .status(IncidentStatus.OPEN).lastActorType(AuditActorType.SYSTEM).build());
                    audit.system(repository, AuditOperation.INCIDENT_CREATED, AuditTargetType.INCIDENT,
                            incident.getId(), "Incident opened from a scan finding");
                } else {
                    if (normalized.severity().ordinal() < incident.getSeverity().ordinal()) {
                        incident.setSeverity(normalized.severity());
                    }
                    if (incident.getStatus() == IncidentStatus.RESOLVED) {
                        incident.setStatus(IncidentStatus.OPEN);
                        incident.setReviewReason(null);
                        incident.setLastActorType(AuditActorType.SYSTEM);
                        incident.setLastActorReference(null);
                        audit.system(repository, AuditOperation.INCIDENT_REOPENED, AuditTargetType.INCIDENT,
                                incident.getId(), "Resolved incident reopened by a new occurrence");
                    }
                    incidents.saveAndFlush(incident);
                }
                newFindings.add(Finding.builder()
                        .scan(scan)
                        .incident(incident)
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
