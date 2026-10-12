package com.secretshield.service;

import java.sql.SQLException;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.dto.PageResponse;
import com.secretshield.dto.RepositoryCreateRequest;
import com.secretshield.dto.RepositoryDto;
import com.secretshield.dto.RepositoryUpdateRequest;
import com.secretshield.entity.ProjectRepository;
import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;
import com.secretshield.exception.ConflictException;
import com.secretshield.exception.ResourceNotFoundException;
import com.secretshield.repository.ProjectRepositoryRepository;
import com.secretshield.security.ResourceAuthorization;
import com.secretshield.validation.GitHubRepositoryMetadata;

import jakarta.validation.ConstraintViolationException;

@Service
public class ProjectRepositoryService {
    private final ProjectRepositoryRepository repositories;
    private final ResourceAuthorization authorization;

    public ProjectRepositoryService(ProjectRepositoryRepository repositories,
            ResourceAuthorization authorization) {
        this.repositories = repositories;
        this.authorization = authorization;
    }

    @Transactional
    public RepositoryDto create(User actor, RepositoryCreateRequest request) {
        requireActor(actor);
        String owner = GitHubRepositoryMetadata.owner(request.githubOwner());
        String name = GitHubRepositoryMetadata.name(request.githubName());
        String branch = GitHubRepositoryMetadata.defaultBranch(request.defaultBranch());
        if (repositories.existsByOwnerIdAndGithubOwnerAndGithubName(actor.getId(), owner, name)) {
            throw new ConflictException("Repository already connected");
        }
        ProjectRepository repository = ProjectRepository.builder()
                .owner(actor).githubOwner(owner).githubName(name)
                .canonicalUrl(GitHubRepositoryMetadata.canonicalUrl(owner, name))
                .defaultBranch(branch).active(true).build();
        try {
            return RepositoryDto.from(repositories.saveAndFlush(repository));
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) { throw new ConflictException("Repository already connected"); }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<RepositoryDto> list(User actor, int page, int size,
            Boolean active, String query, String sort) {
        requireActor(actor);
        if (page < 0 || size < 1 || size > 100) { throw invalid(); }
        String search = query == null ? null : query.strip().toLowerCase(Locale.ROOT);
        if (search == null) { search = ""; }
        if (search.length() > 100) { throw invalid(); }
        Sort ordering = ordering(sort);
        Long ownerId = actor.getRole() == UserRole.ADMIN ? null : actor.getId();
        return PageResponse.from(repositories.findAuthorized(ownerId, active, search,
                PageRequest.of(page, size, ordering)), RepositoryDto::from);
    }

    @Transactional(readOnly = true)
    public RepositoryDto detail(User actor, long id) {
        return RepositoryDto.from(loadAuthorized(actor, id));
    }

    @Transactional
    public RepositoryDto update(User actor, long id, RepositoryUpdateRequest request) {
        ProjectRepository repository = loadAuthorized(actor, id);
        repository.setDefaultBranch(GitHubRepositoryMetadata.defaultBranch(request.defaultBranch()));
        return RepositoryDto.from(repositories.saveAndFlush(repository));
    }

    @Transactional
    public void deactivate(User actor, long id) {
        ProjectRepository repository = loadAuthorized(actor, id);
        if (repository.isActive()) {
            repository.setActive(false);
            repositories.saveAndFlush(repository);
        }
    }

    @Transactional(readOnly = true)
    public ProjectRepository loadActiveAuthorized(User actor, long id) {
        ProjectRepository repository = loadAuthorized(actor, id);
        if (!repository.isActive()) {
            throw new ConflictException("Repository is inactive");
        }
        return repository;
    }

    public ProjectRepository loadAuthorized(User actor, long id) {
        requireActor(actor);
        if (id <= 0) { throw new ResourceNotFoundException("Repository not found"); }
        ProjectRepository repository = repositories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        authorization.requireOwnerOrAdmin(actor, repository.getOwner().getId());
        return repository;
    }

    private static Sort ordering(String requested) {
        String value = requested == null || requested.isBlank() ? "createdAt,desc" : requested.strip();
        String[] parts = value.split(",", -1);
        if (parts.length != 2 || !(parts[0].equals("createdAt") || parts[0].equals("id"))
                || !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc"))) {
            throw invalid();
        }
        Sort.Direction direction = Sort.Direction.fromString(parts[1]);
        Sort primary = Sort.by(direction, parts[0]);
        return parts[0].equals("id") ? primary : primary.and(Sort.by(direction, "id"));
    }

    private static void requireActor(User actor) {
        if (actor == null || actor.getId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
    }

    private static boolean isUniqueViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) { return true; }
        }
        return false;
    }

    private static ConstraintViolationException invalid() {
        return new ConstraintViolationException("Invalid repository query", java.util.Set.of());
    }
}
