package com.secretshield.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.springframework.data.domain.Sort;

import jakarta.validation.ConstraintViolationException;

final class QuerySupport {
    private QuerySupport() {}

    static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid();
    }

    static <T extends Enum<T>> T enumeration(String value, Class<T> type) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException error) { throw invalid(); }
    }

    static LocalDateTime time(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalDateTime(); }
        catch (Exception error) { throw invalid(); }
    }

    static void range(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && !from.isBefore(to)) throw invalid();
    }

    static Sort sort(String requested, String timestamp) {
        String value = requested == null || requested.isBlank() ? timestamp + ",desc" : requested.strip();
        String[] parts = value.split(",", -1);
        if (parts.length != 2 || !(parts[0].equals(timestamp) || parts[0].equals("id"))
                || !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc"))) throw invalid();
        Sort.Direction direction = Sort.Direction.fromString(parts[1]);
        Sort first = Sort.by(direction, parts[0]);
        return parts[0].equals("id") ? first : first.and(Sort.by(direction, "id"));
    }

    private static ConstraintViolationException invalid() {
        return new ConstraintViolationException("Invalid query", Set.of());
    }
}
