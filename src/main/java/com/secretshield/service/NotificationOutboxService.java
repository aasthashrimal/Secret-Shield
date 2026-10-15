package com.secretshield.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.entity.AuditEvent;
import com.secretshield.entity.Incident;
import com.secretshield.entity.NotificationDelivery;
import com.secretshield.entity.NotificationStatus;
import com.secretshield.entity.Severity;
import com.secretshield.repository.NotificationDeliveryRepository;

@Service
public class NotificationOutboxService {
    private final NotificationDeliveryRepository deliveries;
    private final NotificationSettings settings;

    public NotificationOutboxService(NotificationDeliveryRepository deliveries,
            NotificationSettings settings) {
        this.deliveries = deliveries;
        this.settings = settings;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void queue(Incident incident, AuditEvent event) {
        if (!settings.enabled() || !highRisk(incident.getSeverity())) return;
        if (!incident.getRepository().getId().equals(event.getRepository().getId())) {
            throw new IllegalArgumentException("Notification event belongs to another repository");
        }
        for (String reference : settings.recipients().keySet()) {
            if (deliveries.existsByAuditEventIdAndRecipientConfigReference(event.getId(), reference)) continue;
            deliveries.saveAndFlush(NotificationDelivery.builder()
                    .incident(incident).auditEvent(event).recipientConfigReference(reference)
                    .status(NotificationStatus.QUEUED).attemptCount(0)
                    .nextAttemptAt(LocalDateTime.now(ZoneOffset.UTC)).build());
        }
    }

    private static boolean highRisk(Severity severity) {
        return severity == Severity.CRITICAL || severity == Severity.HIGH;
    }
}
