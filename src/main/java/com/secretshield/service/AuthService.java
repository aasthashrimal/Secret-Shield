package com.secretshield.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.secretshield.dto.LoginResponse;
import com.secretshield.entity.User;
import com.secretshield.exception.AuthenticationException;
import com.secretshield.exception.DuplicateUserException;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
import java.util.Locale;
import com.secretshield.repository.UserRepository;
import com.secretshield.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User register(String username, String email, String password) {
        username = username.strip();
        email = email.strip().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserException();
        }

        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUserException();
        }

        String hashedPassword = passwordEncoder.encode(password);

        User user = User.builder()
                .username(username)
                .email(email)
                .password(hashedPassword)
                .role("DEVELOPER")
                .build();

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new DuplicateUserException();
                }
            }
            throw ex;
        }
    }

    public LoginResponse login(String email, String password) {
        email = email.strip().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                    new AuthenticationException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new AuthenticationException("Invalid email or password");
        }

        String token = jwtService.generateToken(
                user.getEmail(),
                user.getRole()
        );

        return new LoginResponse(
                token,
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }
}
