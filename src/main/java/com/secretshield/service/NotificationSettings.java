package com.secretshield.service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationSettings {
    public enum ProviderMode { DISABLED, FAKE, AZURE }

    private final ProviderMode providerMode;
    private final Map<String, String> recipients;
    private final String publicBaseUrl;
    private final String acsConnectionString;
    private final String acsSender;
    private final boolean scheduled;

    public NotificationSettings(
            @Value("${secretshield.notifications.provider:disabled}") String provider,
            @Value("${secretshield.notifications.recipients:}") String recipients,
            @Value("${secretshield.notifications.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${secretshield.notifications.acs.connection-string:}") String acsConnectionString,
            @Value("${secretshield.notifications.acs.sender:}") String acsSender,
            @Value("${secretshield.notifications.scheduled:true}") boolean scheduled) {
        try {
            this.providerMode = ProviderMode.valueOf(provider.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid notification provider mode");
        }
        this.recipients = parseRecipients(recipients);
        this.publicBaseUrl = validateBaseUrl(publicBaseUrl);
        this.acsConnectionString = acsConnectionString;
        this.acsSender = acsSender;
        this.scheduled = scheduled;
        if (providerMode != ProviderMode.DISABLED && this.recipients.isEmpty()) {
            throw new IllegalArgumentException("Enabled notifications require configured recipients");
        }
        if (providerMode == ProviderMode.AZURE &&
                (acsConnectionString.isBlank() || !validEmail(acsSender)
                        || !this.publicBaseUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("Azure notification configuration is incomplete");
        }
    }

    public ProviderMode providerMode() { return providerMode; }
    public boolean enabled() { return providerMode != ProviderMode.DISABLED; }
    public Map<String, String> recipients() { return recipients; }
    public String recipient(String reference) { return recipients.get(reference); }
    public String publicBaseUrl() { return publicBaseUrl; }
    public String acsConnectionString() { return acsConnectionString; }
    public String acsSender() { return acsSender; }
    public boolean scheduled() { return scheduled; }

    private static Map<String, String> parseRecipients(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        var parsed = new LinkedHashMap<String, String>();
        for (String entry : raw.split(",", -1)) {
            String[] parts = entry.strip().split("=", -1);
            if (parts.length != 2 || !parts[0].matches("[a-z][a-z0-9_-]{0,31}")
                    || !validEmail(parts[1]) || parsed.putIfAbsent(parts[0], parts[1]) != null) {
                throw new IllegalArgumentException("Invalid notification recipient configuration");
            }
        }
        if (parsed.size() > 20) throw new IllegalArgumentException("Too many notification recipients");
        return Map.copyOf(parsed);
    }

    private static boolean validEmail(String value) {
        return value != null && value.length() <= 254
                && value.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    }

    private static String validateBaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))) {
                throw new IllegalArgumentException();
            }
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid notification public URL");
        }
    }
}
