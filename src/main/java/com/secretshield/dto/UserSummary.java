package com.secretshield.dto;

import com.secretshield.entity.User;

public record UserSummary(String id, String username, String email, String role) {
    public static UserSummary from(User user) {
        return new UserSummary(user.getId().toString(), user.getUsername(),
                user.getEmail(), user.getRole().name());
    }
}
