package com.secretshield.service;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.dto.IncidentDto;
import com.secretshield.dto.IncidentTransitionRequest;
import com.secretshield.dto.PageResponse;
import com.secretshield.entity.*;
import com.secretshield.exception.ConflictException;
import com.secretshield.exception.ResourceNotFoundException;
import com.secretshield.repository.IncidentRepository;
import com.secretshield.security.ResourceAuthorization;

import jakarta.validation.ConstraintViolationException;

@Service
public class IncidentService {
    private final IncidentRepository incidents;
    private final ProjectRepositoryService repositories;
    private final ResourceAuthorization authorization;
    private final AuditService audit;

    public IncidentService(IncidentRepository incidents, ProjectRepositoryService repositories,
            ResourceAuthorization authorization, AuditService audit) {
        this.incidents = incidents;
        this.repositories = repositories;
        this.authorization = authorization;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentDto> list(User actor, long repositoryId, int page, int size,
            String status, String severity, String from, String to, String sort) {
        var repository = repositories.loadAuthorized(actor, repositoryId);
        QuerySupport.page(page, size);
        IncidentStatus state = QuerySupport.enumeration(status, IncidentStatus.class);
        Severity level = QuerySupport.enumeration(severity, Severity.class);
        LocalDateTime first = QuerySupport.time(from), last = QuerySupport.time(to);
        QuerySupport.range(first, last);
        Specification<Incident> spec = (root, query, cb) ->
                cb.equal(root.get("repository").get("id"), repository.getId());
        if (state != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), state));
        if (level != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("severity"), level));
        if (first != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), first));
        if (last != null) spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), last));
        return PageResponse.from(incidents.findAll(spec,
                PageRequest.of(page, size, QuerySupport.sort(sort, "createdAt"))), IncidentDto::from);
    }

    @Transactional(readOnly = true)
    public IncidentDto detail(User actor, long repositoryId, long incidentId) {
        var repository = repositories.loadAuthorized(actor, repositoryId);
        var incident = incidents.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found"));
        authorization.requireNestedResource(actor, repository.getOwner().getId(), repositoryId,
                incident.getRepository().getId());
        return IncidentDto.from(incident);
    }

    @Transactional
    public IncidentDto transition(User actor, long repositoryId, long incidentId,
            IncidentTransitionRequest request) {
        var repository = repositories.loadAuthorized(actor, repositoryId);
        var incident = incidents.findLockedById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found"));
        authorization.requireNestedResource(actor, repository.getOwner().getId(), repositoryId,
                incident.getRepository().getId());
        if (request.version() == null || request.version() != incident.getVersion()) {
            throw new ConflictException("Incident version changed");
        }
        IncidentStatus next = QuerySupport.enumeration(request.status(), IncidentStatus.class);
        if (next == null || request.reason() == null || request.reason().strip().isEmpty()
                || request.reason().strip().length() > 1000) {
            throw invalid();
        }
        if (next == IncidentStatus.IGNORED) authorization.requireAdmin(actor);
        boolean valid = incident.getStatus() == IncidentStatus.OPEN
                ? next == IncidentStatus.RESOLVED || next == IncidentStatus.IGNORED
                : next == IncidentStatus.OPEN;
        if (!valid) throw new ConflictException("Illegal incident transition");

        incident.setStatus(next);
        incident.setReviewReason(request.reason().strip());
        incident.setLastActorType(AuditActorType.USER);
        incident.setLastActorReference(actor.getId());
        incidents.saveAndFlush(incident);
        AuditOperation operation = switch (next) {
            case RESOLVED -> AuditOperation.INCIDENT_RESOLVED;
            case IGNORED -> AuditOperation.INCIDENT_IGNORED;
            case OPEN -> AuditOperation.INCIDENT_REOPENED;
        };
        audit.user(repository, actor, operation, AuditTargetType.INCIDENT, incident.getId(),
                "Incident review status changed to " + next.name());
        return IncidentDto.from(incident);
    }

    private static ConstraintViolationException invalid() {
        return new ConstraintViolationException("Invalid incident request", Set.of());
    }
}
