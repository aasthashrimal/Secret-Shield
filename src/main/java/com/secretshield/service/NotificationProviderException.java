package com.secretshield.service;

public class NotificationProviderException extends RuntimeException {
    private final String safeCode;

    public NotificationProviderException(String safeCode) {
        super(safeCode);
        this.safeCode = safeCode;
    }

    public String safeCode() { return safeCode; }
}
