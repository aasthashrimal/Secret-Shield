package com.secretshield.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record IncidentTransitionRequest(
        @NotBlank String status,
        @NotBlank @Size(max = 1000) String reason,
        @NotNull @PositiveOrZero Long version) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown incident review field");
    }
}
