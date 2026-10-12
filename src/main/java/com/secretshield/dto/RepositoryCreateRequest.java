package com.secretshield.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RepositoryCreateRequest(
        @NotBlank @Size(max = 39) String githubOwner,
        @NotBlank @Size(max = 100) String githubName,
        @Size(max = 255) String defaultBranch) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown repository request field");
    }
}
