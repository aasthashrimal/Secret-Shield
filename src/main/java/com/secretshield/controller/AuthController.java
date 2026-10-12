package com.secretshield.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.secretshield.dto.UserSummary;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.secretshield.dto.LoginRequest;
import com.secretshield.dto.LoginResponse;
import com.secretshield.dto.RegisterRequest;
import com.secretshield.dto.RegisterResponse;
import com.secretshield.entity.User;
import com.secretshield.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private final AuthService authService;

        public AuthController(AuthService authService) {
                this.authService = authService;
        }

        @GetMapping("/me")
        public UserSummary me(@AuthenticationPrincipal User user) {
                return UserSummary.from(user);
        }

        @PostMapping("/register")
        public ResponseEntity<RegisterResponse> register(
                @Valid @RequestBody RegisterRequest request) {

        User user = authService.register(
                request.username(),
                request.email(),
                request.password()
        );

        RegisterResponse response = new RegisterResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
        }

        @PostMapping("/login")
        public ResponseEntity<LoginResponse> login(
                @Valid @RequestBody LoginRequest request) {

        LoginResponse response = authService.login(
                request.email(),
                request.password()
        );

        return ResponseEntity.ok(response);
}
}
