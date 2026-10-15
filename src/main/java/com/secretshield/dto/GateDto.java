package com.secretshield.dto;

public record GateDto(
        String repositoryId,
        String commitSha,
        String scanId,
        String outcome,
        String reason) {
}
