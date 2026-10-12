package com.secretshield.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.secretshield.dto.UserSummary;
import com.secretshield.entity.User;
import com.secretshield.service.AdminUserService;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService adminUsers;

    public AdminUserController(AdminUserService adminUsers) {
        this.adminUsers = adminUsers;
    }

    @GetMapping("/{id}")
    public UserSummary findUser(@AuthenticationPrincipal User actor, @PathVariable String id) {
        if (!id.matches("[1-9][0-9]{0,18}")) {
            throw new ConstraintViolationException("Invalid resource ID", Set.of());
        }
        try {
            return adminUsers.findUser(actor, Long.parseLong(id));
        } catch (NumberFormatException ex) {
            throw new ConstraintViolationException("Invalid resource ID", Set.of());
        }
    }
}
