package com.secretshield.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.entity.NotificationStatus;
import com.secretshield.repository.NotificationDeliveryRepository;

@Service
public class NotificationClaimService {
    public record Claim(long id, int attempt, String recipientReference,
            String repository, String severity, long repositoryId, long incidentId) {}

    private final NotificationDeliveryRepository deliveries;

    public NotificationClaimService(NotificationDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claimNext() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        deliveries.expireExhausted(now);
        var id = deliveries.lockNextDueId(now);
        if (id.isEmpty()) return null;
        var row = deliveries.findById(id.get()).orElseThrow();
        row.setStatus(NotificationStatus.SENDING);
        row.setAttemptCount(row.getAttemptCount() + 1);
        row.setSafeErrorCode(null);
        row.setLeaseUntil(now.plusMinutes(2));
        deliveries.saveAndFlush(row);
        var incident = row.getIncident();
        var repository = incident.getRepository();
        return new Claim(row.getId(), row.getAttemptCount(), row.getRecipientConfigReference(),
                repository.getGithubOwner() + "/" + repository.getGithubName(),
                incident.getSeverity().name(), repository.getId(), incident.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sent(long id, int attempt) {
        var row = deliveries.findById(id).orElseThrow();
        if (row.getStatus() != NotificationStatus.SENDING || row.getAttemptCount() != attempt) return;
        row.setStatus(NotificationStatus.SENT);
        row.setSafeErrorCode(null);
        row.setLeaseUntil(null);
        deliveries.saveAndFlush(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(long id, int attempt, String safeCode, long retryDelaySeconds) {
        var row = deliveries.findById(id).orElseThrow();
        if (row.getStatus() != NotificationStatus.SENDING || row.getAttemptCount() != attempt) return;
        row.setStatus(NotificationStatus.FAILED);
        row.setSafeErrorCode(safeCode);
        row.setLeaseUntil(null);
        row.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(retryDelaySeconds));
        deliveries.saveAndFlush(row);
    }
}
