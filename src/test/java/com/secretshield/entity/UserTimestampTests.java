package com.secretshield.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTimestampTests {
    @Test void creationInitializesBothTimestampsInUtc() {
        User user = User.builder().createdAt(LocalDateTime.of(2000, 1, 1, 0, 0)).build();
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        user.initializeTimestamps();
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);
        assertFalse(user.getCreatedAt().isBefore(before));
        assertFalse(user.getCreatedAt().isAfter(after));
        assertEquals(user.getCreatedAt(), user.getUpdatedAt());
    }

    @Test void updateChangesOnlyUpdateTimestamp() {
        LocalDateTime original = LocalDateTime.of(2000, 1, 1, 0, 0);
        User user = User.builder().createdAt(original).updatedAt(original).build();
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        user.updateTimestamp();
        assertEquals(original, user.getCreatedAt());
        assertFalse(user.getUpdatedAt().isBefore(before));
        assertFalse(user.getUpdatedAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)));
    }

    @Test void updatingLegacyRowPopulatesMissingUpdateTimestamp() {
        LocalDateTime original = LocalDateTime.of(2000, 1, 1, 0, 0);
        User user = User.builder().createdAt(original).build();
        assertNull(user.getUpdatedAt());
        user.updateTimestamp();
        assertNotNull(user.getUpdatedAt());
        assertEquals(original, user.getCreatedAt());
    }
}
