package com.secretshield.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.communication.email.models.EmailSendStatus;
import com.azure.core.util.polling.SyncPoller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class NotificationProviderTests {
    private static final String SOURCE_VALUE = "raw-secret-value-that-must-stay-private";

    @Test
    void azureProviderSendsOnlySafeFieldsAndAcceptsSuccessfulOperation() {
        EmailClient client = mock(EmailClient.class);
        @SuppressWarnings("unchecked")
        SyncPoller<EmailSendResult, EmailSendResult> poller = mock(SyncPoller.class);
        EmailSendResult result = mock(EmailSendResult.class);
        when(client.beginSend(any(EmailMessage.class))).thenReturn(poller);
        when(poller.getFinalResult(any(Duration.class))).thenReturn(result);
        when(result.getStatus()).thenReturn(EmailSendStatus.SUCCEEDED);
        var provider = new AcsEmailNotificationProvider(settings(), client);
        var message = new NotificationMessage("recipient@example.invalid", "demo/repo", "HIGH",
                "https://app.example.invalid/api/repositories/1/incidents/2");

        provider.send(message);

        ArgumentCaptor<EmailMessage> captured = ArgumentCaptor.forClass(EmailMessage.class);
        verify(client).beginSend(captured.capture());
        assertEquals("sender@example.invalid", captured.getValue().getSenderAddress());
        assertEquals(message.subject(), captured.getValue().getSubject());
        assertEquals(message.body(), captured.getValue().getBodyPlainText());
        assertEquals("recipient@example.invalid",
                captured.getValue().getToRecipients().getFirst().getAddress());
        assertFalse(captured.getValue().getBodyPlainText().contains(SOURCE_VALUE));
    }

    @Test
    void providerFailuresExposeOnlyFixedSafeCodes() {
        EmailClient client = mock(EmailClient.class);
        when(client.beginSend(any(EmailMessage.class)))
                .thenThrow(new IllegalStateException("endpoint included " + SOURCE_VALUE));
        var provider = new AcsEmailNotificationProvider(settings(), client);
        var message = new NotificationMessage("recipient@example.invalid", "demo/repo", "CRITICAL",
                "https://app.example.invalid/api/repositories/1/incidents/2");

        var error = assertThrows(NotificationProviderException.class, () -> provider.send(message));
        assertEquals("PROVIDER_ERROR", error.safeCode());
        assertFalse(error.toString().contains(SOURCE_VALUE));
        assertNull(error.getCause());
    }

    @Test
    void azureConfigurationRequiresPrivateCredentialsAndHttpsLink() {
        var missing = assertThrows(IllegalArgumentException.class,
                () -> new NotificationSettings("azure", "security=recipient@example.invalid",
                        "http://localhost:8080", "", "sender@example.invalid", false));
        assertFalse(missing.getMessage().contains("recipient@example.invalid"));
        assertThrows(IllegalArgumentException.class,
                () -> new NotificationSettings("fake", "security=bad-address",
                        "http://localhost:8080", "", "", false));
    }

    @Test
    void backgroundPollLogsNoInfrastructureExceptionDetails() {
        NotificationClaimService claims = mock(NotificationClaimService.class);
        when(claims.claimNext()).thenThrow(new IllegalStateException(SOURCE_VALUE));
        var settings = new NotificationSettings("fake", "security=recipient@example.invalid",
                "http://localhost:8080", "", "", true);
        var dispatcher = new NotificationDispatcher(settings, claims,
                mock(FakeNotificationProvider.class), mock(AcsEmailNotificationProvider.class));
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(NotificationDispatcher.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            dispatcher.scheduledDispatch();
            assertEquals(1, appender.list.size());
            assertEquals("Notification outbox poll failed", appender.list.getFirst().getFormattedMessage());
            assertFalse(appender.list.getFirst().toString().contains(SOURCE_VALUE));
            assertNull(appender.list.getFirst().getThrowableProxy());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static NotificationSettings settings() {
        return new NotificationSettings("azure", "security=recipient@example.invalid",
                "https://app.example.invalid", "synthetic-test-connection-string",
                "sender@example.invalid", false);
    }
}
