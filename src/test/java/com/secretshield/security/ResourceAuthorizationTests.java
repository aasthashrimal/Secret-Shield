package com.secretshield.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;
import com.secretshield.exception.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

class ResourceAuthorizationTests {
    private final ResourceAuthorization authorization = new ResourceAuthorization();

    private User user(long id, UserRole role) {
        return User.builder().id(id).username("synthetic-" + id)
                .email("user-" + id + "@example.invalid")
                .password("synthetic-hash").role(role).build();
    }

    @Test void developerCanAccessOwnedRepository() {
        assertDoesNotThrow(() -> authorization.requireOwnerOrAdmin(user(1, UserRole.DEVELOPER), 1));
    }

    @Test void developerCannotAccessAnotherOwnersRepository() {
        assertThrows(AccessDeniedException.class,
                () -> authorization.requireOwnerOrAdmin(user(1, UserRole.DEVELOPER), 2));
    }

    @Test void adminCanAccessAnotherOwnersRepository() {
        assertDoesNotThrow(() -> authorization.requireOwnerOrAdmin(user(1, UserRole.ADMIN), 2));
    }

    @Test void developerCannotPerformAdminAction() {
        assertThrows(AccessDeniedException.class,
                () -> authorization.requireAdmin(user(1, UserRole.DEVELOPER)));
        assertDoesNotThrow(() -> authorization.requireAdmin(user(2, UserRole.ADMIN)));
    }

    @Test void nestedResourceMustBelongToRouteRepository() {
        User owner = user(1, UserRole.DEVELOPER);
        assertDoesNotThrow(() -> authorization.requireNestedResource(owner, 1, 10, 10));
        assertThrows(ResourceNotFoundException.class,
                () -> authorization.requireNestedResource(owner, 1, 10, 11));
    }

    @Test void nestedCheckRejectsUnauthorizedParentBeforeRevealingChildRelationship() {
        assertThrows(AccessDeniedException.class,
                () -> authorization.requireNestedResource(user(1, UserRole.DEVELOPER), 2, 10, 11));
    }
}
