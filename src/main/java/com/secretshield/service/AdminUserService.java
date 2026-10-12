package com.secretshield.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.dto.UserSummary;
import com.secretshield.entity.User;
import com.secretshield.exception.ResourceNotFoundException;
import com.secretshield.repository.UserRepository;
import com.secretshield.security.ResourceAuthorization;

@Service
public class AdminUserService {
    private final UserRepository users;
    private final ResourceAuthorization authorization;

    public AdminUserService(UserRepository users, ResourceAuthorization authorization) {
        this.users = users;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public UserSummary findUser(User actor, long id) {
        authorization.requireAdmin(actor);
        if (id <= 0) { throw new ResourceNotFoundException("User not found"); }
        return users.findById(id).map(UserSummary::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
