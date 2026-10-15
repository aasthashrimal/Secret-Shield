package com.secretshield.dto;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.secretshield.entity.NotificationDelivery;

public record NotificationDto(
        String id,
        String incidentId,
        String auditEventId,
        String recipientConfigReference,
        String status,
        int attemptCount,
        String safeErrorCode,
        String createdAt,
        String updatedAt) {
    public static NotificationDto from(NotificationDelivery delivery) {
        return new NotificationDto(delivery.getId().toString(),
                delivery.getIncident().getId().toString(),
                delivery.getAuditEvent().getId().toString(),
                delivery.getRecipientConfigReference(), delivery.getStatus().name(),
                delivery.getAttemptCount(), delivery.getSafeErrorCode(),
                utc(delivery.getCreatedAt()), utc(delivery.getUpdatedAt()));
    }

    private static String utc(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toString();
    }
}
