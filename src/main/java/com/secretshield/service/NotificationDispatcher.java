package com.secretshield.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatcher {
    private final NotificationSettings settings;
    private final NotificationClaimService claims;
    private final FakeNotificationProvider fake;
    private final AcsEmailNotificationProvider azure;

    public NotificationDispatcher(NotificationSettings settings, NotificationClaimService claims,
            FakeNotificationProvider fake, AcsEmailNotificationProvider azure) {
        this.settings = settings;
        this.claims = claims;
        this.fake = fake;
        this.azure = azure;
    }

    @Scheduled(initialDelayString = "${secretshield.notifications.initial-delay-ms:10000}",
            fixedDelayString = "${secretshield.notifications.poll-delay-ms:10000}")
    public void scheduledDispatch() {
        if (!settings.scheduled()) return;
        try {
            dispatchAvailable();
        } catch (RuntimeException error) {
            // The next poll retries. Exception text may include infrastructure secrets.
            org.slf4j.LoggerFactory.getLogger(NotificationDispatcher.class)
                    .warn("Notification outbox poll failed");
        }
    }

    public int dispatchAvailable() {
        if (!settings.enabled()) return 0;
        int processed = 0;
        while (processed < 20) {
            NotificationClaimService.Claim claim = claims.claimNext();
            if (claim == null) break;
            processed++;
            String recipient = settings.recipient(claim.recipientReference());
            if (recipient == null) {
                claims.failed(claim.id(), claim.attempt(), "RECIPIENT_NOT_CONFIGURED", 30);
                continue;
            }
            NotificationMessage message = new NotificationMessage(recipient,
                    claim.repository(), claim.severity(),
                    settings.publicBaseUrl() + "/api/repositories/" + claim.repositoryId()
                            + "/incidents/" + claim.incidentId());
            try {
                provider().send(message);
                claims.sent(claim.id(), claim.attempt());
            } catch (NotificationProviderException error) {
                claims.failed(claim.id(), claim.attempt(), error.safeCode(), retryDelay(claim.attempt()));
            } catch (RuntimeException error) {
                claims.failed(claim.id(), claim.attempt(), "PROVIDER_ERROR", retryDelay(claim.attempt()));
            }
        }
        return processed;
    }

    private NotificationProvider provider() {
        return switch (settings.providerMode()) {
            case FAKE -> fake;
            case AZURE -> azure;
            case DISABLED -> throw new IllegalStateException("Notifications disabled");
        };
    }

    private static long retryDelay(int attempt) {
        return attempt == 1 ? 30 : 120;
    }
}
