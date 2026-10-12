package com.secretshield.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RepositoryUpdateRequest(
        @NotBlank @Size(max = 255) String defaultBranch) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown repository request field");
    }
}
