package com.secretshield.controller;

import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.secretshield.dto.*;
import com.secretshield.entity.User;
import com.secretshield.service.AuditHistoryService;
import com.secretshield.service.IncidentService;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/repositories/{repositoryId}")
public class IncidentController {
    private final IncidentService incidents;
    private final AuditHistoryService history;

    public IncidentController(IncidentService incidents, AuditHistoryService history) {
        this.incidents = incidents;
        this.history = history;
    }

    @GetMapping("/incidents")
    public PageResponse<IncidentDto> list(@AuthenticationPrincipal User actor,
            @PathVariable String repositoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return incidents.list(actor, id(repositoryId), page, size, status, severity, from, to, sort);
    }

    @GetMapping("/incidents/{incidentId}")
    public IncidentDto detail(@AuthenticationPrincipal User actor,
            @PathVariable String repositoryId, @PathVariable String incidentId) {
        return incidents.detail(actor, id(repositoryId), id(incidentId));
    }

    @PatchMapping("/incidents/{incidentId}")
    public IncidentDto transition(@AuthenticationPrincipal User actor,
            @PathVariable String repositoryId, @PathVariable String incidentId,
            @Valid @RequestBody IncidentTransitionRequest request) {
        return incidents.transition(actor, id(repositoryId), id(incidentId), request);
    }

    @GetMapping("/audit-events")
    public PageResponse<AuditEventDto> history(@AuthenticationPrincipal User actor,
            @PathVariable String repositoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "occurredAt,desc") String sort) {
        return history.list(actor, id(repositoryId), page, size, operation, actorType, from, to, sort);
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
