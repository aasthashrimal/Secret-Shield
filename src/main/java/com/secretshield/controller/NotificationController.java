package com.secretshield.controller;

import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.secretshield.dto.NotificationDto;
import com.secretshield.dto.PageResponse;
import com.secretshield.entity.User;
import com.secretshield.service.NotificationHistoryService;

import jakarta.validation.ConstraintViolationException;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/incidents/{incidentId}/notifications")
public class NotificationController {
    private final NotificationHistoryService history;

    public NotificationController(NotificationHistoryService history) {
        this.history = history;
    }

    @GetMapping
    public PageResponse<NotificationDto> list(@AuthenticationPrincipal User actor,
            @PathVariable String repositoryId, @PathVariable String incidentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return history.list(actor, id(repositoryId), id(incidentId), page, size, status, sort);
    }

    private static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.parseLong(value); }
        catch (NumberFormatException error) { throw invalid(); }
    }

    private static ConstraintViolationException invalid() {
        return new ConstraintViolationException("Invalid resource ID", Set.of());
    }
}
