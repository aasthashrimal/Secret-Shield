package com.secretshield.dto;

import java.time.ZoneOffset;

import com.secretshield.entity.AuditEvent;

public record AuditEventDto(
        String id,
        String repositoryId,
        String actorType,
        String actorUserId,
        String actorCredentialId,
        String operation,
        String targetType,
        String targetId,
        String safeChangeSummary,
        String occurredAt) {
    public static AuditEventDto from(AuditEvent event) {
        return new AuditEventDto(event.getId().toString(), event.getRepository().getId().toString(),
                event.getActorType().name(),
                event.getActorUser() == null ? null : event.getActorUser().getId().toString(),
                event.getActorCredential() == null ? null : event.getActorCredential().getId().toString(),
                event.getOperation().name(), event.getTargetType().name(), event.getTargetId().toString(),
                event.getSafeChangeSummary(), event.getOccurredAt().toInstant(ZoneOffset.UTC).toString());
    }
}
