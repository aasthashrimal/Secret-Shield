package com.secretshield.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.entity.*;
import com.secretshield.repository.AuditEventRepository;

@Service
public class AuditService {
    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent user(ProjectRepository repository, User actor, AuditOperation operation,
            AuditTargetType target, long targetId, String summary) {
        return record(repository, AuditActorType.USER, actor, null, operation, target, targetId, summary);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent machine(ProjectRepository repository, CiCredential credential, AuditOperation operation,
            AuditTargetType target, long targetId, String summary) {
        if (!credential.getRepository().getId().equals(repository.getId())) {
            throw new IllegalArgumentException("Credential belongs to another repository");
        }
        return record(repository, AuditActorType.MACHINE, null, credential, operation, target, targetId, summary);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent system(ProjectRepository repository, AuditOperation operation,
            AuditTargetType target, long targetId, String summary) {
        return record(repository, AuditActorType.SYSTEM, null, null, operation, target, targetId, summary);
    }

    private AuditEvent record(ProjectRepository repository, AuditActorType actorType, User actor,
            CiCredential credential, AuditOperation operation, AuditTargetType target,
            long targetId, String summary) {
        return events.saveAndFlush(AuditEvent.builder().repository(repository).actorType(actorType)
                .actorUser(actor).actorCredential(credential).operation(operation)
                .targetType(target).targetId(targetId).safeChangeSummary(summary).build());
    }
}
