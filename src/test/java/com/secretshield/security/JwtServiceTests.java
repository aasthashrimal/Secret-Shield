package com.secretshield.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTests {
    @Test void invalidSecretFailsWithSafeMessage() {
        for (String secret : new String[]{"", " ", "synthetic-short", "${JWT_SECRET}"}) {
            var failure = assertThrows(IllegalArgumentException.class, () -> new JwtService(secret, 3600000));
            assertEquals("JWT_SECRET must be configured with at least 32 UTF-8 bytes", failure.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> new JwtService(null, 3600000));
    }
    @Test void invalidExpirationFailsClearly() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("synthetic-test-only-signing-key-32-bytes", 0));
    }
    @Test void generatedTokenIsSignedAndHasExpectedSubject() {
        var service = new JwtService("synthetic-test-only-signing-key-32-bytes", 3600000);
        String token = service.generateToken("test@example.invalid", "DEVELOPER");
        assertTrue(service.isTokenValid(token), "Generated token must verify");
        assertEquals("test@example.invalid", service.extractUsername(token));
        var other = new JwtService("synthetic-different-signing-key-32-bytes", 3600000);
        assertFalse(other.isTokenValid(token), "Different signing key must reject token");
    }
}
