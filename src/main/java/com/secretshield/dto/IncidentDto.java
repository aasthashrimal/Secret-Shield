package com.secretshield.dto;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.secretshield.entity.Incident;

public record IncidentDto(
        String id,
        String repositoryId,
        String fingerprint,
        String severity,
        String status,
        String reviewReason,
        String lastActorType,
        String lastActorReference,
        long version,
        String createdAt,
        String updatedAt) {
    public static IncidentDto from(Incident incident) {
        return new IncidentDto(incident.getId().toString(), incident.getRepository().getId().toString(),
                incident.getFingerprint(), incident.getSeverity().name(), incident.getStatus().name(),
                incident.getReviewReason(),
                incident.getLastActorType() == null ? null : incident.getLastActorType().name(),
                incident.getLastActorReference() == null ? null : incident.getLastActorReference().toString(),
                incident.getVersion(), utc(incident.getCreatedAt()), utc(incident.getUpdatedAt()));
    }

    private static String utc(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toString();
    }
}
