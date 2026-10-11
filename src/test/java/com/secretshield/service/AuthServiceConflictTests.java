package com.secretshield.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.secretshield.entity.User;
import com.secretshield.exception.ConflictException;
import com.secretshield.repository.UserRepository;
import com.secretshield.security.JwtService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceConflictTests {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    @Mock JwtService tokens;
    private AuthService service;

    @BeforeEach void setup() {
        service = new AuthService(users, passwords, tokens);
    }

    @Test void duplicateEmailUsesConflictExceptionWithoutSaving() {
        when(users.existsByEmail("test@example.invalid")).thenReturn(true);
        assertThrows(ConflictException.class,
                () -> service.register("tester", "test@example.invalid", "synthetic-password"));
        verify(users, never()).save(any(User.class));
        verify(passwords, never()).encode(any());
    }

    @Test void duplicateUsernameUsesConflictExceptionWithoutSaving() {
        when(users.existsByUsername("tester")).thenReturn(true);
        assertThrows(ConflictException.class,
                () -> service.register("tester", "test@example.invalid", "synthetic-password"));
        verify(users, never()).save(any(User.class));
        verify(passwords, never()).encode(any());
    }
}
