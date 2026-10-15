package com.secretshield.service;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.secretshield.entity.Severity;

@Component
public class FindingSeverityPolicy {
    private static final Map<String, Severity> MAPPING = Map.ofEntries(
            Map.entry("private-key", Severity.CRITICAL),
            Map.entry("aws-access-token", Severity.CRITICAL),
            Map.entry("generic-api-key", Severity.HIGH),
            Map.entry("github-pat", Severity.HIGH),
            Map.entry("github-fine-grained-pat", Severity.HIGH),
            Map.entry("generic-password", Severity.MEDIUM),
            Map.entry("synthetic-low", Severity.LOW),
            Map.entry("synthetic-info", Severity.INFO));

    public Severity severityFor(String ruleId) {
        return MAPPING.getOrDefault(ruleId, Severity.HIGH);
    }
}
