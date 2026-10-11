package com.secretshield.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.secretshield.entity.User;
import com.secretshield.exception.ConflictException;
import com.secretshield.exception.DuplicateUserException;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
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

    @Test void uniqueConstraintRaceUsesExplicitDuplicateException() {
        when(passwords.encode(any())).thenReturn("synthetic-hash");
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "synthetic private database detail", new SQLException("synthetic-private-detail", "23505")));
        assertThrows(DuplicateUserException.class,
                () -> service.register("tester", " TEST@EXAMPLE.INVALID ", "synthetic-password"));
        verify(users).existsByEmail("test@example.invalid");
    }

    @Test void otherIntegrityFailuresAreNotReportedAsDuplicates() {
        when(passwords.encode(any())).thenReturn("synthetic-hash");
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "synthetic private detail", new SQLException("synthetic-private-detail", "23502")));
        assertThrows(DataIntegrityViolationException.class,
                () -> service.register("tester", "test@example.invalid", "synthetic-password"));
    }
}
