package com.secretshield.controller;

import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.secretshield.dto.FindingDto;
import com.secretshield.dto.PageResponse;
import com.secretshield.entity.User;
import com.secretshield.service.FindingService;

import jakarta.validation.ConstraintViolationException;

@RestController
@RequestMapping("/api/repositories/{repositoryId}")
public class FindingController {
    private final FindingService findings;

    public FindingController(FindingService findings) {
        this.findings = findings;
    }

    @GetMapping("/scans/{scanId}/findings")
    public PageResponse<FindingDto> list(
            @AuthenticationPrincipal User actor,
            @PathVariable String repositoryId,
            @PathVariable String scanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return findings.list(actor, id(repositoryId), id(scanId), page, size,
                severity, ruleId, from, to, sort);
    }

    @GetMapping("/findings/{findingId}")
    public FindingDto detail(
            @AuthenticationPrincipal User actor,
            @PathVariable String repositoryId,
            @PathVariable String findingId) {
        return findings.detail(actor, id(repositoryId), id(findingId));
    }

    private static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw invalid();
        }
        try {
            return Long.parseLong(value);
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static ConstraintViolationException invalid() {
        return new ConstraintViolationException("Invalid resource ID", Set.of());
    }
}
