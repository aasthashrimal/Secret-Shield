package com.secretshield.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.dto.AuditEventDto;
import com.secretshield.dto.PageResponse;
import com.secretshield.entity.AuditActorType;
import com.secretshield.entity.AuditEvent;
import com.secretshield.entity.AuditOperation;
import com.secretshield.entity.User;
import com.secretshield.repository.AuditEventRepository;

@Service
public class AuditHistoryService {
    private final AuditEventRepository events;
    private final ProjectRepositoryService repositories;

    public AuditHistoryService(AuditEventRepository events, ProjectRepositoryService repositories) {
        this.events = events;
        this.repositories = repositories;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventDto> list(User actor, long repositoryId, int page, int size,
            String operation, String actorType, String from, String to, String sort) {
        var repository = repositories.loadAuthorized(actor, repositoryId);
        QuerySupport.page(page, size);
        AuditOperation action = QuerySupport.enumeration(operation, AuditOperation.class);
        AuditActorType type = QuerySupport.enumeration(actorType, AuditActorType.class);
        LocalDateTime first = QuerySupport.time(from), last = QuerySupport.time(to);
        QuerySupport.range(first, last);
        Specification<AuditEvent> spec = (root, query, cb) ->
                cb.equal(root.get("repository").get("id"), repository.getId());
        if (action != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("operation"), action));
        if (type != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("actorType"), type));
        if (first != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), first));
        if (last != null) spec = spec.and((root, query, cb) -> cb.lessThan(root.get("occurredAt"), last));
        return PageResponse.from(events.findAll(spec,
                PageRequest.of(page, size, QuerySupport.sort(sort, "occurredAt"))), AuditEventDto::from);
    }
}
