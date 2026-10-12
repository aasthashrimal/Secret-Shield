package com.secretshield.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.entity.UserRole;
import com.secretshield.repository.UserRepository;

@Service
public class AdminBootstrapService {
    private final UserRepository users;

    public AdminBootstrapService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public void promoteExistingUser(String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Administrator bootstrap email is required");
        }
        var user = users.findByEmail(normalized)
                .orElseThrow(() -> new IllegalStateException("Administrator bootstrap user was not found"));
        if (user.getRole() != UserRole.ADMIN) {
            user.setRole(UserRole.ADMIN);
            users.saveAndFlush(user);
        }
    }
}
