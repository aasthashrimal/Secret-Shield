package com.secretshield.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;
import com.secretshield.exception.ResourceNotFoundException;

@Component
public class ResourceAuthorization {
    public void requireAdmin(User actor) {
        if (actor == null || actor.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Administrator access required");
        }
    }

    public void requireOwnerOrAdmin(User actor, long ownerId) {
        if (actor == null || actor.getId() == null) {
            throw new AccessDeniedException("Authentication required");
        }
        if (actor.getRole() != UserRole.ADMIN && actor.getId() != ownerId) {
            throw new AccessDeniedException("Resource access denied");
        }
    }

    /**
     * Call after loading the route's repository. It checks access to that parent first,
     * then prevents a nested resource from being used under another repository URL.
     */
    public void requireNestedResource(User actor, long repositoryOwnerId,
            long routeRepositoryId, long resourceRepositoryId) {
        requireOwnerOrAdmin(actor, repositoryOwnerId);
        if (routeRepositoryId != resourceRepositoryId) {
            throw new ResourceNotFoundException("Nested resource not found");
        }
    }
}
