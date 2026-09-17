package com.secretshield.dto;

public record RegisterResponse(
        Long id,
        String username,
        String email,
        String role
) {
}