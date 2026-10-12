package com.secretshield.dto;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.secretshield.entity.ProjectRepository;

public record RepositoryDto(
        String id,
        String ownerId,
        String githubOwner,
        String githubName,
        String canonicalUrl,
        String defaultBranch,
        boolean active,
        String createdAt,
        String updatedAt) {

    public static RepositoryDto from(ProjectRepository repository) {
        return new RepositoryDto(
                repository.getId().toString(),
                repository.getOwner().getId().toString(),
                repository.getGithubOwner(),
                repository.getGithubName(),
                repository.getCanonicalUrl(),
                repository.getDefaultBranch(),
                repository.isActive(),
                utc(repository.getCreatedAt()),
                utc(repository.getUpdatedAt()));
    }

    private static String utc(java.time.LocalDateTime value) {
        return DateTimeFormatter.ISO_INSTANT.format(value.toInstant(ZoneOffset.UTC));
    }
}
