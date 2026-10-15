package com.secretshield.controller;

import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.secretshield.dto.GateDto;
import com.secretshield.entity.User;
import com.secretshield.service.GateDecisionService;

import jakarta.validation.ConstraintViolationException;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/gate")
public class GateController {
    private final GateDecisionService decisions;

    public GateController(GateDecisionService decisions) {
        this.decisions = decisions;
    }

    @GetMapping
    public GateDto decide(@AuthenticationPrincipal User actor,
            @PathVariable String repositoryId,
            @RequestParam String commitSha) {
        return decisions.decide(actor, id(repositoryId), commitSha);
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
