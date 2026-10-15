package com.secretshield.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.dto.FindingDto;
import com.secretshield.dto.PageResponse;
import com.secretshield.entity.Finding;
import com.secretshield.entity.Severity;
import com.secretshield.entity.User;
import com.secretshield.exception.ResourceNotFoundException;
import com.secretshield.repository.FindingRepository;
import com.secretshield.repository.ScanRepository;
import com.secretshield.security.ResourceAuthorization;

import jakarta.validation.ConstraintViolationException;

@Service
public class FindingService {
    private final FindingRepository findings;
    private final ScanRepository scans;
    private final ProjectRepositoryService repositories;
    private final ResourceAuthorization authorization;

    public FindingService(FindingRepository findings, ScanRepository scans,
            ProjectRepositoryService repositories, ResourceAuthorization authorization) {
        this.findings = findings;
        this.scans = scans;
        this.repositories = repositories;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public PageResponse<FindingDto> list(User actor, long repositoryId, long scanId,
            int page, int size, String severity, String ruleId,
            String from, String to, String sort) {
        var repository = repositories.loadAuthorized(actor, repositoryId);
        var scan = scans.findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found"));
        authorization.requireNestedResource(actor, repository.getOwner().getId(),
                repositoryId, scan.getRepository().getId());
        validatePage(page, size);
        Severity requestedSeverity = parseSeverity(severity);
        String requestedRule = parseRule(ruleId);
        LocalDateTime fromTime = parseTime(from);
        LocalDateTime toTime = parseTime(to);
        if (fromTime != null && toTime != null && !fromTime.isBefore(toTime)) {
            throw invalid();
        }

        Specification<Finding> specification = (root, query, builder) ->
                builder.equal(root.get("scan").get("id"), scanId);
        if (requestedSeverity != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("severity"), requestedSeverity));
        }
        if (requestedRule != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("ruleId"), requestedRule));
        }
        if (fromTime != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("createdAt"), fromTime));
        }
        if (toTime != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThan(root.get("createdAt"), toTime));
        }
        return PageResponse.from(findings.findAll(specification,
                PageRequest.of(page, size, ordering(sort))), FindingDto::from);
    }

    @Transactional(readOnly = true)
    public FindingDto detail(User actor, long repositoryId, long findingId) {
        var repository = repositories.loadAuthorized(actor, repositoryId);
        var finding = findings.findById(findingId)
                .orElseThrow(() -> new ResourceNotFoundException("Finding not found"));
        authorization.requireNestedResource(actor, repository.getOwner().getId(),
                repositoryId, finding.getScan().getRepository().getId());
        return FindingDto.from(finding);
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalid();
        }
    }

    private static Severity parseSeverity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Severity.valueOf(value);
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static String parseRule(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String rule = value.strip();
        if (!rule.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")) {
            throw invalid();
        }
        return rule;
    }

    private static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static Sort ordering(String requested) {
        String value = requested == null || requested.isBlank() ? "createdAt,desc" : requested;
        String[] parts = value.split(",", -1);
        if (parts.length != 2
                || !(parts[0].equals("createdAt") || parts[0].equals("id"))
                || !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc"))) {
            throw invalid();
        }
        Sort.Direction direction = Sort.Direction.fromString(parts[1]);
        Sort primary = Sort.by(direction, parts[0]);
        return parts[0].equals("id") ? primary : primary.and(Sort.by(direction, "id"));
    }

    private static ConstraintViolationException invalid() {
        return new ConstraintViolationException("Invalid finding query", Set.of());
    }
}
