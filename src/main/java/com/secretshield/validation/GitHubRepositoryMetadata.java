package com.secretshield.validation;

import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintViolationException;

public final class GitHubRepositoryMetadata {
    private static final Pattern OWNER = Pattern.compile(
            "[a-z0-9](?:[a-z0-9-]{0,37}[a-z0-9])?");
    private static final Pattern NAME = Pattern.compile("[a-z0-9._-]{1,100}");
    private static final Pattern BRANCH = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,254}");

    private GitHubRepositoryMetadata() {
    }

    public static String owner(String value) {
        String normalized = normalizeIdentity(value);
        if (!OWNER.matcher(normalized).matches() || normalized.contains("--")) {
            throw invalid();
        }
        return normalized;
    }

    public static String name(String value) {
        String normalized = normalizeIdentity(value);
        if (!NAME.matcher(normalized).matches()
                || normalized.equals(".") || normalized.equals("..")) {
            throw invalid();
        }
        return normalized;
    }

    public static String defaultBranch(String value) {
        String branch = value == null || value.isBlank() ? "main" : value.strip();
        if (!BRANCH.matcher(branch).matches()
                || branch.endsWith(".") || branch.endsWith("/")
                || branch.contains("..") || branch.contains("//")
                || branch.contains("@{") || branch.contains("/.")) {
            throw invalid();
        }
        for (String part : branch.split("/")) {
            if (part.endsWith(".lock")) { throw invalid(); }
        }
        return branch;
    }

    public static String canonicalUrl(String owner, String name) {
        return "https://github.com/" + owner + "/" + name;
    }

    private static String normalizeIdentity(String value) {
        if (value == null) { throw invalid(); }
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static ConstraintViolationException invalid() {
        return new ConstraintViolationException("Invalid GitHub repository metadata", java.util.Set.of());
    }
}
