package com.secretshield.service;

public record NotificationMessage(
        String recipient,
        String repository,
        String severity,
        String incidentUrl) {
    public String subject() {
        return "Secret Shield: " + severity + " finding in " + repository;
    }

    public String body() {
        return "A high-risk finding was detected in " + repository + ".\n"
                + "Severity: " + severity + "\n"
                + "Review the incident: " + incidentUrl + "\n"
                + "Rotate or revoke any exposed credential at its provider, remove it from source, "
                + "and run a new scan. Incident review does not unblock deployment.\n";
    }
}
