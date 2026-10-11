package com.secretshield.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.secretshield.entity.User;
import com.secretshield.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.JwtException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final JsonSecurityErrorHandler errorHandler;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            JsonSecurityErrorHandler errorHandler) {

        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.errorHandler = errorHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Liveness must not depend on a bearer token or the user database.
        return "GET".equals(request.getMethod())
                && (request.getContextPath() + "/api/health/live").equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        User user = null;
        try {
            if (jwtService.isTokenValid(token)) {
                String email = jwtService.extractUsername(token);
                user = email == null || email.isBlank() ? null : userRepository
                        .findByEmail(email.strip().toLowerCase(java.util.Locale.ROOT)).orElse(null);
            }
        } catch (JwtException | IllegalArgumentException ex) {
            // A token can expire between the validation and claims reads.
            SecurityContextHolder.clearContext();
            errorHandler.commence(request, response,
                    new org.springframework.security.authentication.BadCredentialsException("Invalid token"));
            return;
        } catch (org.springframework.dao.DataAccessException ex) {
            SecurityContextHolder.clearContext();
            errorHandler.write(response, HttpStatus.INTERNAL_SERVER_ERROR);
            return;
        }

        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority("ROLE_" + user.getRole());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(authority)
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
