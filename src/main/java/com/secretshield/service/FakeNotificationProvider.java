package com.secretshield.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class FakeNotificationProvider implements NotificationProvider {
    private final List<NotificationMessage> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresRemaining = new AtomicInteger();

    @Override
    public void send(NotificationMessage message) {
        if (failuresRemaining.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
            throw new NotificationProviderException("SIMULATED_FAILURE");
        }
        sent.add(message);
    }

    public List<NotificationMessage> sentMessages() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
        failuresRemaining.set(0);
    }

    public void failNext(int count) {
        if (count < 0) throw new IllegalArgumentException("Invalid failure count");
        failuresRemaining.set(count);
    }
}
