package com.secretshield.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendStatus;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;

@Component
public class AcsEmailNotificationProvider implements NotificationProvider {
    private final NotificationSettings settings;
    private volatile EmailClient client;

    @Autowired
    public AcsEmailNotificationProvider(NotificationSettings settings) {
        this.settings = settings;
    }

    AcsEmailNotificationProvider(NotificationSettings settings, EmailClient client) {
        this.settings = settings;
        this.client = client;
    }

    @Override
    public void send(NotificationMessage message) {
        EmailMessage email = new EmailMessage()
                .setSenderAddress(settings.acsSender())
                .setToRecipients(message.recipient())
                .setSubject(message.subject())
                .setBodyPlainText(message.body());
        try {
            var result = client().beginSend(email).getFinalResult(Duration.ofSeconds(30));
            if (result == null || !EmailSendStatus.SUCCEEDED.equals(result.getStatus())) {
                throw new NotificationProviderException("PROVIDER_REJECTED");
            }
        } catch (NotificationProviderException error) {
            throw error;
        } catch (RuntimeException error) {
            // Never include SDK exception text: it may contain headers or endpoint details.
            throw new NotificationProviderException("PROVIDER_ERROR");
        }
    }

    private EmailClient client() {
        EmailClient current = client;
        if (current != null) return current;
        synchronized (this) {
            if (client == null) {
                client = new EmailClientBuilder()
                        .connectionString(settings.acsConnectionString())
                        .httpLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.NONE))
                        .buildClient();
            }
            return client;
        }
    }
}
