package com.secretshield.controller;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.secretshield.dto.PageResponse;
import com.secretshield.dto.RepositoryCreateRequest;
import com.secretshield.dto.RepositoryDto;
import com.secretshield.dto.RepositoryUpdateRequest;
import com.secretshield.entity.User;
import com.secretshield.service.ProjectRepositoryService;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/repositories")
public class ProjectRepositoryController {
    private final ProjectRepositoryService repositories;

    public ProjectRepositoryController(ProjectRepositoryService repositories) {
        this.repositories = repositories;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepositoryDto create(@AuthenticationPrincipal User actor,
            @Valid @RequestBody RepositoryCreateRequest request) {
        return repositories.create(actor, request);
    }

    @GetMapping
    public PageResponse<RepositoryDto> list(@AuthenticationPrincipal User actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return repositories.list(actor, page, size, active, query, sort);
    }

    @GetMapping("/{id}")
    public RepositoryDto detail(@AuthenticationPrincipal User actor, @PathVariable String id) {
        return repositories.detail(actor, parseId(id));
    }

    @PatchMapping("/{id}")
    public RepositoryDto update(@AuthenticationPrincipal User actor, @PathVariable String id,
            @Valid @RequestBody RepositoryUpdateRequest request) {
        return repositories.update(actor, parseId(id), request);
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal User actor, @PathVariable String id) {
        repositories.deactivate(actor, parseId(id));
    }

    private static long parseId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) { throw invalid(); }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw invalid();
        }
    }

    private static ConstraintViolationException invalid() {
        return new ConstraintViolationException("Invalid resource ID", Set.of());
    }
}
